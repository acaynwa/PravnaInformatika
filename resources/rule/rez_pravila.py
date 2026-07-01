#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import argparse
import json
import sys
import traceback

from pathlib import Path

from case_facts import CaseFactsExtractor
from dr_device_reasoner import DrDeviceReasoner
from rule_parser import RuleParser
from sentence_calculator import SentenceCalculator


if sys.platform == 'win32':
    import codecs

    sys.stdout = codecs.getwriter('utf-8')(sys.stdout.buffer, 'strict')


class InlineFactsAdapter:

    def __init__(self, facts_dict):
        self.facts = facts_dict

    def get_fact(self, key, default=None):
        return self.facts.get(key, default)

    def has_article(self, pattern):
        return any(pattern in str(article) for article in self.facts.get('articles', []))

    def has_txt(self):
        return bool(self.facts.get('txt_facts'))


def resolve_rules_file(rules_file: str = None) -> Path:
    if rules_file:
        return Path(rules_file)

    project_root = Path(__file__).parent.parent
    default_path = project_root / 'rule' / 'dr-device' / 'rulebase.lrml'
    if default_path.exists():
        return default_path
    return project_root / 'fallback ' / 'legalruleml' / 'rulebase.lrml'


def build_result_template():
    return {
        "status": "success",
        "case_number": "Nepoznato",
        "articles": [],
        "verdict": "Nepoznato",
        "actual_sentence": "N/A",
        "applicable_articles": [],
        "violated_rules": [],
        "penalty_range": {"min": "N/A", "max": "N/A"},
        "mitigating_factors": [],
        "aggravating_factors": [],
        "recommendation": "N/A",
        "detailed_analysis": [],
        "acquittal": False,
    }


def reason_about_case(case_file: str = None, facts: str = None, rules_file: str = None):
    try:
        rules_path = resolve_rules_file(rules_file)
        if not rules_path.exists():
            return {
                "status": "error",
                "error": f"Rules file not found: {rules_path}",
            }

        result = build_result_template()
        rule_parser = RuleParser(str(rules_path))

        if case_file:
            case_path = Path(case_file)
            if not case_path.exists():
                return {
                    "status": "error",
                    "error": f"Case file not found: {case_file}",
                }

            case_facts = CaseFactsExtractor(case_file)
            analysis = SentenceCalculator(rule_parser, case_facts).analyze_case()
            result.update(analysis)

            try:
                result['dr_device_reasoning'] = DrDeviceReasoner(case_facts.facts).run()
            except Exception as error:
                result['dr_device_reasoning'] = {'status': 'error', 'reason': str(error)}

        elif facts:
            try:
                facts_dict = json.loads(facts)
            except json.JSONDecodeError as error:
                return {
                    "status": "error",
                    "error": f"Invalid JSON facts: {error}",
                }

            case_facts = InlineFactsAdapter(facts_dict)
            analysis = SentenceCalculator(rule_parser, case_facts).analyze_case()
            result.update(analysis)

            try:
                result['dr_device_reasoning'] = DrDeviceReasoner(facts_dict).run()
            except Exception as error:
                result['dr_device_reasoning'] = {'status': 'error', 'reason': str(error)}

        return result

    except Exception as error:
        return {
            "status": "error",
            "error": str(error),
            "traceback": traceback.format_exc(),
        }


def main():
    parser = argparse.ArgumentParser(
        description="Montenegrin Legal Case Reasoning Engine - Environmental Crimes (Art. 325/326 KZ CG)"
    )
    parser.add_argument('--case', help='XML case file path')
    parser.add_argument('--facts', help='JSON facts string')
    parser.add_argument('--rules', help='Legal rules LegalRuleML file path')
    parser.add_argument('--json', action='store_true', help='Output as JSON')

    args = parser.parse_args()
    result = reason_about_case(
        case_file=args.case,
        facts=args.facts,
        rules_file=args.rules,
    )

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
