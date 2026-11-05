"""Synthetic metadata for validator unit tests only, never language qualification evidence."""

import copy
import json
from pathlib import Path
import tempfile
import unittest
from contextlib import redirect_stdout, redirect_stderr
from io import StringIO

import validate_language_trials as validator


def metadata():
    dialogue = [{"id": f"dialogue-{index}", "reviewed": True, "held_out": True,
                 "clear_dialogue": True, "reference": "unit-test reference only", "scenario": "clear_dialogue",
                 "display_caption_sha256": "c" * 64, "helper_caption_sha256": "d" * 64,
                 "confident": True, "correct_scene": True, "acquisition_ms": 20_000,
                 "absolute_timing_error_ms": 500} for index in range(30)]
    scenarios = sorted(validator.REQUIRED_NEGATIVE_SCENARIOS)
    negatives = [{"id": f"negative-{index}", "reviewed": True, "held_out": True,
                  "reference": "unit-test reference only", "scenario": scenarios[index % len(scenarios)],
                  "confident": False, "correct_scene": False} for index in range(20)]
    return {"schema_version": 1, "groups": [{
        "platform": "android", "spoken_language": "es", "caption_language": "en",
        "matching_path": "direct_translation",
        "device": {"model": "Unit-test device", "os_version": "16", "api_level": 36,
                   "physical_device": True, "arm64": True, "recent_flagship": True,
                   "app_revision": "unit-test", "recognizer_revision": "unit-test",
                   "app_artifact_sha256": "a" * 64, "model_sha256": validator.MODEL_HASHES["multilingual"],
                   "audit_reference": "unit-test, not physical evidence"},
        "review": {"reviewer": "Unit test", "native_speaker": True, "held_out": True,
                   "corpus_id": "unit-test", "review_reference": "unit-test only"},
        "evidence": {"native_asr_used": True, "airplane_mode_sync_verified": True,
                     "memory_bounded": True, "model_initialization_in_deadline": True,
                     "audio_saved": False, "audio_uploaded": False, "background_capture_events": 0,
                     "max_pending_windows": 1, "max_active_recognition_jobs": 1,
                     "worst_attempt_duration_ms": 34_000},
        "dialogue_trials": dialogue, "negative_trials": negatives}]}


class ValidatorTest(unittest.TestCase):
    def test_acceptance_boundary_counts_all_trials(self):
        document = metadata()
        trials = document["groups"][0]["dialogue_trials"]
        for trial in trials[:3]:
            trial.update(confident=False, correct_scene=False)
        report = validator.validate(document)
        self.assertTrue(report["qualified"])
        self.assertEqual(0.9, report["groups"][0]["metrics"]["acquisition_success_rate"])
        trials[3].update(confident=False, correct_scene=False)
        self.assertFalse(validator.validate(document)["qualified"])

    def test_late_correct_match_is_a_failure_for_acquisition_rate(self):
        document = metadata()
        for trial in document["groups"][0]["dialogue_trials"][:4]:
            trial["acquisition_ms"] = 30_001
        self.assertFalse(validator.validate(document)["qualified"])

    def test_rejects_incomplete_corpus_and_duplicate_ids(self):
        for modification in ("few_dialogue", "few_negative", "duplicate"):
            with self.subTest(modification=modification):
                document = metadata()
                group = document["groups"][0]
                if modification == "few_dialogue": group["dialogue_trials"].pop()
                if modification == "few_negative": group["negative_trials"].pop()
                if modification == "duplicate": group["dialogue_trials"][1]["id"] = "dialogue-0"
                self.assertFalse(validator.validate(document)["qualified"])

    def test_rejects_confident_wrong_dialogue_or_negative_selection(self):
        for kind in ("dialogue_trials", "negative_trials"):
            document = metadata()
            document["groups"][0][kind][0].update(confident=True, correct_scene=False)
            self.assertFalse(validator.validate(document)["qualified"])

    def test_rejects_missing_repeated_dialogue_and_privacy_evidence(self):
        document = metadata()
        for trial in document["groups"][0]["negative_trials"]:
            trial["scenario"] = "silence"
        self.assertFalse(validator.validate(document)["qualified"])
        for key, value in (("audio_saved", True), ("audio_uploaded", True),
                           ("background_capture_events", 1), ("max_pending_windows", 2),
                           ("native_asr_used", False), ("airplane_mode_sync_verified", False)):
            with self.subTest(key=key):
                document = metadata()
                document["groups"][0]["evidence"][key] = value
                self.assertFalse(validator.validate(document)["qualified"])

    def test_median_is_strictly_below_one_second(self):
        document = metadata()
        for trial in document["groups"][0]["dialogue_trials"]:
            trial["absolute_timing_error_ms"] = 1_000
        self.assertFalse(validator.validate(document)["qualified"])

    def test_rejects_emulators_unknown_pairs_wrong_models_and_unreviewed_audio(self):
        for change in ("emulator", "pair", "hash", "review", "deadline", "boolean_number"):
            with self.subTest(change=change):
                document = metadata()
                group = document["groups"][0]
                if change == "emulator": group["device"]["physical_device"] = False
                if change == "pair": group["spoken_language"] = "yue"
                if change == "hash": group["device"]["model_sha256"] = "b" * 64
                if change == "review": group["review"]["native_speaker"] = False
                if change == "deadline": group["evidence"]["worst_attempt_duration_ms"] = 35_001
                if change == "boolean_number": group["dialogue_trials"][0]["acquisition_ms"] = True
                self.assertFalse(validator.validate(document)["qualified"])

    def test_ios_qualification_is_separate_from_android(self):
        document = metadata()
        ios = copy.deepcopy(document["groups"][0])
        ios.update(platform="ios", matching_path="matching_helper")
        ios["device"].update(model="iPhone 17 Pro", os_version="26.2")
        document["groups"].append(ios)
        self.assertTrue(validator.validate(document)["qualified"])
        ios["device"]["model"] = "iPhone simulator"
        self.assertFalse(validator.validate(document)["qualified"])

    def test_missing_and_malformed_reports_fail(self):
        for document in (None, [], {}, {"schema_version": True, "groups": []},
                         {"schema_version": 1, "groups": [None]}):
            self.assertFalse(validator.validate(document)["qualified"])
        document = metadata()
        document["groups"][0].update(platform=[], spoken_language={}, matching_path=[])
        self.assertFalse(validator.validate(document)["qualified"])

    def test_cli_exit_codes_and_json_report(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / "input.json"
            output = Path(directory) / "report.json"
            source.write_text(json.dumps(metadata()), encoding="utf-8")
            with redirect_stdout(StringIO()), redirect_stderr(StringIO()):
                self.assertEqual(0, validator.main([str(source), "--output", str(output)]))
                self.assertTrue(json.loads(output.read_text())["qualified"])
                source.write_text("{}", encoding="utf-8")
                self.assertEqual(1, validator.main([str(source)]))
                source.write_text('{"value": NaN}', encoding="utf-8")
                self.assertEqual(2, validator.main([str(source)]))


if __name__ == "__main__":
    unittest.main()
