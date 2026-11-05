#!/usr/bin/env python3
"""Validate supplied device trial metadata. This does not generate or attest trial evidence."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path
import re
import statistics
import sys
from typing import Any

MIN_DIALOGUE_TRIALS = 30
MIN_NEGATIVE_TRIALS = 20
MAX_ACQUISITION_MS = 30_000
MAX_ATTEMPT_MS = 35_000
TARGET_LANGUAGES = {"zh", "ja", "es", "fr", "de", "ko", "hi", "te", "ta"}
REQUIRED_NEGATIVE_SCENARIOS = {
    "repeated_dialogue", "incorrect_caption_track", "silence", "music_only", "background_conversation"
}
MODEL_HASHES = {
    "en": "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f",
    "multilingual": "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
}


def _number(value: Any) -> bool:
    return type(value) in (int, float) and -9_007_199_254_740_991 <= value <= 9_007_199_254_740_991 and math.isfinite(value)


def _nonempty(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _sha256(value: Any) -> bool:
    return isinstance(value, str) and re.fullmatch(r"[a-f0-9]{64}", value) is not None


def validate_group(group: Any, ordinal: int) -> dict[str, Any]:
    errors: list[str] = []
    metrics: dict[str, Any] = {}
    result = {"group": ordinal, "qualified": False, "errors": errors, "metrics": metrics}
    if not isinstance(group, dict):
        errors.append("Group must be an object.")
        return result
    platform = group.get("platform")
    spoken = group.get("spoken_language")
    path = group.get("matching_path")
    result.update(platform=platform, spoken_language=spoken, matching_path=path)
    if not isinstance(platform, str) or platform not in {"android", "ios"}:
        errors.append("Platform must be android or ios.")
    if group.get("caption_language") != "en":
        errors.append("Only English-display auto-seek qualifies in this version.")
    allowed_paths = {"english"} if spoken == "en" else {"matching_helper", "direct_translation"}
    if spoken != "en" and (not isinstance(spoken, str) or spoken not in TARGET_LANGUAGES):
        errors.append("Spoken language is outside the target matrix.")
    if not isinstance(path, str) or path not in allowed_paths:
        errors.append("Matching path does not apply to this spoken language.")

    device = group.get("device")
    if not isinstance(device, dict):
        errors.append("Device metadata is missing.")
        device = {}
    for field in ("model", "os_version", "app_revision", "recognizer_revision", "audit_reference"):
        if not _nonempty(device.get(field)):
            errors.append(f"Device {field} must identify the tested device, build or evidence.")
    if device.get("physical_device") is not True or device.get("arm64") is not True:
        errors.append("Qualification requires a physical ARM64 device, not a simulator or emulator.")
    if not _sha256(device.get("app_artifact_sha256")):
        errors.append("App artifact SHA-256 is required to identify the exact tested build.")
    expected_model = MODEL_HASHES["en" if spoken == "en" else "multilingual"]
    if device.get("model_sha256") != expected_model:
        errors.append("Speech model SHA-256 does not match the pinned artifact for this pair.")
    if platform == "android":
        api = device.get("api_level")
        if type(api) is not int or api < 33 or device.get("recent_flagship") is not True:
            errors.append("Android qualification requires API 33+ and an identified recent flagship.")
    if platform == "ios":
        model = str(device.get("model", "")).lower().replace(" ", "")
        if model != "iphone17pro":
            errors.append("Initial iOS qualification requires iPhone 17 Pro.")
        version = device.get("os_version")
        if not isinstance(version, str) or re.fullmatch(r"\d+(?:\.\d+){0,2}", version) is None:
            errors.append("iOS os_version must be a numeric version.")
        else:
            components = tuple(int(part) for part in version.split("."))
            if (components + (0, 0))[:2] < (26, 2):
                errors.append("Initial iOS qualification requires iOS 26.2 or newer.")

    review = group.get("review")
    if not isinstance(review, dict):
        errors.append("Native-speaker corpus review is missing.")
        review = {}
    for field in ("reviewer", "corpus_id", "review_reference"):
        if not _nonempty(review.get(field)):
            errors.append(f"Review {field} is required.")
    if review.get("native_speaker") is not True or review.get("held_out") is not True:
        errors.append("Use held-out dialogue reviewed by a native speaker of the spoken language.")

    evidence = group.get("evidence")
    if not isinstance(evidence, dict):
        errors.append("Privacy, lifecycle and offline evidence is missing.")
        evidence = {}
    for field in ("native_asr_used", "airplane_mode_sync_verified", "memory_bounded", "model_initialization_in_deadline"):
        if evidence.get(field) is not True:
            errors.append(f"Evidence {field} must be verified true.")
    for field in ("audio_saved", "audio_uploaded"):
        if evidence.get(field) is not False:
            errors.append(f"Evidence {field} must be verified false.")
    background = evidence.get("background_capture_events")
    if type(background) is not int or background != 0:
        errors.append("Background capture events must be explicitly zero.")
    pending = evidence.get("max_pending_windows")
    active = evidence.get("max_active_recognition_jobs")
    if type(pending) is not int or not 0 <= pending <= 1:
        errors.append("At most one pending audio window is permitted.")
    if type(active) is not int or active != 1:
        errors.append("Exactly one maximum active recognition job must be observed.")
    worst_duration = evidence.get("worst_attempt_duration_ms")
    if not _number(worst_duration) or not 0 < worst_duration <= MAX_ATTEMPT_MS:
        errors.append("Attempts, including initialization and both passes, must end within 35 seconds.")

    dialogue = group.get("dialogue_trials")
    negatives = group.get("negative_trials")
    if not isinstance(dialogue, list):
        errors.append("dialogue_trials must be an array.")
        dialogue = []
    if not isinstance(negatives, list):
        errors.append("negative_trials must be an array.")
        negatives = []
    if len(dialogue) < MIN_DIALOGUE_TRIALS:
        errors.append(f"At least {MIN_DIALOGUE_TRIALS} dialogue trials are required per group.")
    if len(negatives) < MIN_NEGATIVE_TRIALS:
        errors.append(f"At least {MIN_NEGATIVE_TRIALS} negative trials are required per group.")
    ids: set[str] = set()
    successes = 0
    accepted_errors: list[float] = []
    scenarios: set[str] = set()
    for kind, trials in (("dialogue", dialogue), ("negative", negatives)):
        for index, trial in enumerate(trials):
            label = f"{kind} trial {index + 1}"
            if not isinstance(trial, dict):
                errors.append(f"{label} must be an object.")
                continue
            identity = trial.get("id")
            if not _nonempty(identity) or identity in ids:
                errors.append(f"{label} needs a unique nonempty id.")
            else:
                ids.add(identity)
            for field in ("reviewed", "held_out"):
                if trial.get(field) is not True:
                    errors.append(f"{label} must be {field}.")
            if not _nonempty(trial.get("reference")):
                errors.append(f"{label} needs a reviewable timing or negative-case reference.")
            if kind == "dialogue" and trial.get("clear_dialogue") is not True:
                errors.append(f"{label} must belong to the held-out clear-dialogue corpus.")
            if kind == "dialogue":
                if not _sha256(trial.get("display_caption_sha256")):
                    errors.append(f"{label} needs the tested display caption SHA-256.")
                if path == "matching_helper" and not _sha256(trial.get("helper_caption_sha256")):
                    errors.append(f"{label} needs the tested helper caption SHA-256.")
            scenario = trial.get("scenario")
            if not _nonempty(scenario):
                errors.append(f"{label} needs a scenario.")
            elif kind == "negative":
                scenarios.add(scenario)
            confident = trial.get("confident")
            correct = trial.get("correct_scene")
            if type(confident) is not bool or type(correct) is not bool:
                errors.append(f"{label} requires explicit boolean confident and correct_scene fields.")
            if confident is True and (kind == "negative" or correct is not True):
                errors.append(f"{label} contains a confident wrong or ambiguous selection.")
            if confident is True and correct is True and kind == "dialogue":
                acquisition = trial.get("acquisition_ms")
                timing_error = trial.get("absolute_timing_error_ms")
                if not _number(acquisition) or not 0 < acquisition <= MAX_ATTEMPT_MS:
                    errors.append(f"{label} needs a positive acquisition_ms within the 35-second attempt deadline.")
                elif acquisition <= MAX_ACQUISITION_MS:
                    successes += 1
                if not _number(timing_error) or timing_error < 0:
                    errors.append(f"{label} needs a nonnegative absolute_timing_error_ms.")
                else:
                    accepted_errors.append(float(timing_error))
    missing = REQUIRED_NEGATIVE_SCENARIOS - scenarios
    if missing:
        errors.append("Missing negative scenarios: " + ", ".join(sorted(missing)) + ".")
    ratio = successes / len(dialogue) if dialogue else 0.0
    median = statistics.median(accepted_errors) if accepted_errors else None
    metrics.update(dialogue_trials=len(dialogue), negative_trials=len(negatives),
                   correct_within_30_seconds=successes, acquisition_success_rate=ratio,
                   accepted_trials=len(accepted_errors), median_absolute_timing_error_ms=median)
    if ratio < 0.9:
        errors.append("Fewer than 90% of clear-dialogue trials acquired the correct scene within 30 seconds.")
    if median is None or median >= 1_000:
        errors.append("Median accepted absolute timing error must be strictly below 1000 ms.")
    result["qualified"] = not errors
    return result


def validate(document: Any) -> dict[str, Any]:
    errors: list[str] = []
    if not isinstance(document, dict):
        return {"qualified": False, "errors": ["Report must be an object."], "groups": []}
    if type(document.get("schema_version")) is not int or document["schema_version"] != 1:
        errors.append("schema_version must be integer 1.")
    supplied = document.get("groups")
    if not isinstance(supplied, list) or not supplied:
        return {"qualified": False, "errors": errors + ["At least one device/language/path group is required."], "groups": []}
    groups = [validate_group(group, index + 1) for index, group in enumerate(supplied)]
    return {"qualified": not errors and all(group["qualified"] for group in groups),
            "errors": errors, "groups": groups,
            "notice": "This checks supplied metadata. It does not attest device execution or independently verify referenced evidence."}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="JSON containing supplied device trial metadata")
    parser.add_argument("--output", type=Path, help="Optional JSON result path")
    arguments = parser.parse_args(argv)
    try:
        document = json.loads(arguments.input.read_text(encoding="utf-8"),
                              parse_constant=lambda value: (_ for _ in ()).throw(ValueError(f"Invalid JSON number: {value}")))
        report = validate(document)
        rendered = json.dumps(report, indent=2, ensure_ascii=False, allow_nan=False) + "\n"
        if arguments.output:
            arguments.output.write_text(rendered, encoding="utf-8")
        print(rendered, end="")
        return 0 if report["qualified"] else 1
    except (OSError, UnicodeError, ValueError, TypeError) as error:
        print(f"Could not validate trial report: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
