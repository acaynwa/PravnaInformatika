import sys
import xml.etree.ElementTree as ET

from typing import Dict, Optional

from rule_models import PenaltyRange


class RuleParser:

    def __init__(self, rules_file: str):
        self.rules_file = rules_file
        self.rules = {}
        self.mitigating_rules = []
        self.aggravating_rules = []
        self.sentencing_rules = []
        self.confiscation_rules = []
        self.penalties = {}
        self.rule_to_penalty = {}
        self.parse_rules()

    def parse_rules(self):
        try:
            tree = ET.parse(self.rules_file)
            root = tree.getroot()

            ns = {
                'lrml': 'http://docs.oasis-open.org/legalruleml/ns/v1.0/',
                'ruleml': 'http://ruleml.org/spec',
                'xs': 'http://www.w3.org/2001/XMLSchema',
                'xsi': 'http://www.w3.org/2001/XMLSchema-instance',
            }

            for stmt in root.findall('.//lrml:PrescriptiveStatement', ns):
                key = stmt.get('key', '')
                rule_elem = stmt.find('ruleml:Rule', ns)
                if rule_elem is None:
                    continue

                rule_info = {
                    'key': key,
                    'rule_key': rule_elem.get('key', ''),
                    'paraphrase': self._key_to_paraphrase(key),
                    'if': rule_elem.find('ruleml:if', ns),
                    'then': rule_elem.find('ruleml:then', ns),
                    'element': rule_elem,
                }

                key_lower = key.lower()
                if 'mitigating' in key_lower:
                    self.mitigating_rules.append(rule_info)
                elif 'aggravating' in key_lower:
                    self.aggravating_rules.append(rule_info)
                elif 'confiscation' in key_lower or 'fishing_4' in key_lower or 'hunting_5' in key_lower:
                    self.confiscation_rules.append(rule_info)
                else:
                    self.rules[key] = rule_info

            for pen_stmt in root.findall('.//lrml:PenaltyStatement', ns):
                pen_key = pen_stmt.get('key', '')
                ind_elem = pen_stmt.find('.//ruleml:Ind', ns)
                if ind_elem is not None:
                    try:
                        self.penalties[pen_key] = int(ind_elem.text.strip())
                    except (ValueError, TypeError):
                        pass

            for rep_stmt in root.findall('.//lrml:ReparationStatement', ns):
                rep = rep_stmt.find('lrml:Reparation', ns)
                if rep is None:
                    continue
                pen_ref = rep.find('lrml:appliesPenalty', ns)
                ps_ref = rep.find('lrml:toPrescriptiveStatement', ns)
                if pen_ref is not None and ps_ref is not None:
                    pen_key = pen_ref.get('keyref', '').lstrip('#')
                    ps_key = ps_ref.get('keyref', '').lstrip('#')
                    if pen_key in self.penalties and ps_key in self.rules:
                        self.rule_to_penalty[ps_key] = self.penalties[pen_key]

        except Exception as error:
            print(f"Error parsing rules: {error}", file=sys.stderr)

    def _key_to_paraphrase(self, key: str) -> str:
        descriptions = {
            'ps_joint_perpetration': 'Saizvršilaštvo (čl. 23 st. 2 KZ CG)',
            'ps_hunting_1': 'Nezakonit lov u lovostaju ili zabranjenom području (čl. 325 st. 1)',
            'ps_hunting_2': 'Lov u tuđem lovištu uz ubijanje/hvatanje divljači (čl. 325 st. 2)',
            'ps_hunting_3': 'Lov u tuđem lovištu - krupna divljač (čl. 325 st. 3)',
            'ps_hunting_4': 'Lov zaštićene vrste / bez posebne dozvole / sredstvima za masovno uništavanje (čl. 325 st. 4)',
            'ps_hunting_5': 'Oduzimanje predmeta za lov (čl. 325 st. 5)',
            'ps_fishing_1': 'Ribolov u lovostaju ili zabranjenim vodama (čl. 326 st. 1)',
            'ps_fishing_2': 'Ribolov zabranjenim sredstvima (čl. 326 st. 2)',
            'ps_fishing_3': 'Ribolov velike količine ili velike vrijednosti (čl. 326 st. 3)',
            'ps_fishing_4': 'Oduzimanje predmeta za ribolov (čl. 326 st. 4)',
        }
        return descriptions.get(key, key)

    def extract_penalty_from_rule(self, rule: Dict) -> PenaltyRange:
        key = rule.get('key', '')
        penalty_days = self.rule_to_penalty.get(key)
        if penalty_days is not None:
            return PenaltyRange("30 dana", f"{penalty_days} dana")
        return PenaltyRange()

    def extract_threshold_from_rule(self, rule: Dict) -> Optional[int]:
        return None
