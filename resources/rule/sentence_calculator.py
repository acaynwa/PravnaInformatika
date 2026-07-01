import re

from typing import Dict, List, Optional

from rule_models import PenaltyRange


class SentenceCalculator:

    def __init__(self, rule_parser, case_facts):
        self.rule_parser = rule_parser
        self.case_facts = case_facts
        self.applicable_rules = []
        self.mitigating_factors = []
        self.aggravating_factors = []
        self.base_penalty = PenaltyRange()

    def _fact_is_yes(self, key: str) -> bool:
        val = self.case_facts.get_fact(key)
        if val is True:
            return True
        if val is False or val is None:
            return False
        return str(val).lower().strip() in ('da', 'yes', 'true', '1')

    def analyze_case(self) -> Dict:
        verdict = self.case_facts.get_fact('verdict', 'Nepoznato')
        verdict_lower = str(verdict).lower() if verdict else ''
        is_acquittal = 'oslobod' in verdict_lower or 'oslobađ' in verdict_lower

        txt_facts = self.case_facts.get_fact('txt_facts', {})
        raw_sentence = self.case_facts.get_fact('actual_sentence', 'N/A')
        is_conditional = self.case_facts.get_fact('conditional_sentence', False)
        txt_sentence_text = txt_facts.get('actual_sentence_text', '')

        if txt_sentence_text:
            display_sentence = txt_sentence_text
        elif is_conditional and raw_sentence and raw_sentence != 'N/A':
            display_sentence = f"Uslovna presuda: {raw_sentence}"
        elif raw_sentence and raw_sentence not in ('N/A', 'Nepoznat', 'None', ''):
            display_sentence = raw_sentence
        else:
            display_sentence = 'N/A'

        analysis = {
            'case_number': self.case_facts.get_fact('case_number', 'Nepoznato'),
            'articles': self.case_facts.get_fact('articles', []),
            'verdict': verdict,
            'actual_sentence': display_sentence,
            'applicable_articles': [],
            'violated_rules': [],
            'applicable_rules': [],
            'penalty_range': PenaltyRange().to_dict(),
            'mitigating_factors': [],
            'aggravating_factors': [],
            'recommendation': "N/A",
            'analysis': [],
            'acquittal': is_acquittal,
            'metadata': {
                'court': self.case_facts.get_fact('court', ''),
                'judge': self.case_facts.get_fact('judge', ''),
                'court_clerk': self.case_facts.get_fact('court_clerk', ''),
                'date': self.case_facts.get_fact('date', ''),
                'year': self.case_facts.get_fact('year', ''),
                'defendant': self.case_facts.get_fact('defendant', ''),
                'defendant_parents': self.case_facts.get_fact('defendant_parents', ''),
                'prior_convictions': self.case_facts.get_fact('prior_convictions_text', ''),
                'crime_type': self.case_facts.get_fact('crime_type', ''),
            },
            'case_facts': {
                'description': self.case_facts.get_fact('case_description', '') or self.case_facts.get_fact('case_summary', ''),
                'witnesses': self.case_facts.get_fact('witnesses', []),
                'evidence': self.case_facts.get_fact('evidence', []),
                'zabranjenoSredstvo': self.case_facts.get_fact('zabranjenoSredstvo', ''),
                'elektricnaStruja': self.case_facts.get_fact('elektricnaStruja', ''),
                'agregat': self.case_facts.get_fact('agregat', ''),
                'sonda': self.case_facts.get_fact('sonda', ''),
                'pretvarac': self.case_facts.get_fact('pretvarac', ''),
                'prisutanUlov': self.case_facts.get_fact('prisutanUlov', ''),
                'kolicinaUlovaKg': self.case_facts.get_fact('kolicinaUlovaKg', 0),
                'oduzimanjePredmeta': self.case_facts.get_fact('oduzimanjePredmeta', ''),
                'lovostajIliZabranjeneVode': self.case_facts.get_fact('lovostajIliZabranjeneVode', ''),
                'crime_date': self.case_facts.get_fact('crime_date', ''),
                'crime_location': self.case_facts.get_fact('crime_location_name', ''),
            },
            'legal_analysis': {
                'verdict_reason': self.case_facts.get_fact('verdict_reason', ''),
                'legal_principle': self.case_facts.get_fact('legal_principle', ''),
                'court_costs': self.case_facts.get_fact('court_costs', ''),
                'prosecutor': self.case_facts.get_fact('prosecutor', ''),
                'defense_attorney': self.case_facts.get_fact('defense_attorney', ''),
            },
            'has_txt': self.case_facts.has_txt() if hasattr(self.case_facts, 'has_txt') else False,
        }

        if is_acquittal:
            analysis['actual_verdict_note'] = 'Optuženi je u stvarnom postupku oslobođen od optužbe'
            if self.case_facts.get_fact('verdict_reason'):
                analysis['legal_analysis']['verdict_reason'] = self.case_facts.get_fact('verdict_reason')

        if 'opomena' in verdict_lower:
            analysis['actual_sentence'] = 'Sudska opomena'

        for article in self.case_facts.get_fact('articles', []):
            analysis['applicable_articles'].append(article)

            article_num = self._extract_article_number(article)
            article_para = self._extract_article_paragraph(article)
            if article_num:
                article_rules = self._get_applicable_rules_for_article(article_num, article_para)

                for rule in article_rules:
                    analysis['violated_rules'].append(rule.get('paraphrase', 'Rule'))
                    analysis['applicable_rules'].append({
                        'key': rule.get('key'),
                        'paraphrase': rule.get('paraphrase'),
                    })

                if article_rules:
                    best_rule = None
                    if article_para:
                        for rule in article_rules:
                            key = rule.get('key', '')
                            if f'_{article_para}' in key:
                                penalty = self.rule_parser.extract_penalty_from_rule(rule)
                                if penalty.min != 'N/A' or penalty.max != 'N/A':
                                    best_rule = rule
                                    break

                    if not best_rule:
                        for rule in article_rules:
                            penalty = self.rule_parser.extract_penalty_from_rule(rule)
                            if penalty.min != 'N/A' or penalty.max != 'N/A':
                                best_rule = rule
                                break

                    if best_rule:
                        penalty = self.rule_parser.extract_penalty_from_rule(best_rule)
                        analysis['penalty_range'] = penalty.to_dict()
                        self.base_penalty = penalty

        if not analysis['applicable_articles']:
            verdict = str(self.case_facts.get_fact('verdict', '')).lower()
            if 'oslobod' in verdict or 'oslobađ' in verdict:
                analysis['violated_rules'].append('Optuženi je oslobođen - krivično delo nije dokazano')
                analysis['penalty_range'] = {'min': 'N/A', 'max': 'N/A', 'note': 'Oslobađajuća presuda'}
                analysis['acquittal'] = True
            elif 'osud' in verdict or 'kriv' in verdict:
                analysis['violated_rules'].append('Utvrđena krivična odgovornost')
                analysis['penalty_range'] = {'min': '30 dana', 'max': '1095 dana'}

        self._identify_mitigating_factors(analysis)
        self._identify_aggravating_factors(analysis)
        analysis['recommendation'] = self._calculate_recommended_sentence(analysis)
        analysis['analysis'] = self._generate_analysis(analysis)
        return analysis

    def _extract_article_number(self, article_str: str) -> Optional[str]:
        text = str(article_str or '').strip()
        match = re.search(r'(?:čl\.?|cl\.?|član|clan)\s*(\d+)', text, re.IGNORECASE)
        if match:
            return match.group(1)

        compact = re.search(r'\b(\d{2,3})(?:\.(\d{1,2}))?\b', text)
        if compact:
            return compact.group(1)
        return None

    def _extract_article_paragraph(self, article_str: str) -> Optional[str]:
        text = str(article_str or '').strip()
        match = re.search(r'st(?:av)?\.?\s*(\d+)', text, re.IGNORECASE)
        if match:
            return match.group(1)

        compact = re.search(r'\b\d{2,3}\.(\d{1,2})\b', text)
        if compact:
            return compact.group(1)
        return None

    def _get_applicable_rules_for_article(self, article_num: str, article_para: Optional[str] = None) -> List[Dict]:
        matching_rules = []
        article_to_prefix = {
            '325': 'hunting',
            '326': 'fishing',
            '23': 'joint',
        }
        prefix = article_to_prefix.get(article_num, '')
        if not prefix:
            return matching_rules

        for key, rule in self.rule_parser.rules.items():
            key_lower = key.lower()
            if prefix in key_lower:
                if article_para:
                    if f'_{prefix}_{article_para}' in key_lower:
                        matching_rules.append(rule)
                else:
                    matching_rules.append(rule)
        return matching_rules

    def _identify_mitigating_factors(self, analysis: Dict):
        txt = self.case_facts.get_fact('txt_facts', {})
        prior = self.case_facts.get_fact('previously_convicted')
        if prior is None:
            prior = txt.get('prior_convictions', None)
        if not prior:
            self.mitigating_factors.append('Ranije neosudivan')
            analysis['mitigating_factors'].append('Ranije neosudivan (čl. 42)')

        confession = txt.get('confession', None)
        if confession is None:
            confession = self.case_facts.get_fact('confession', None)
        if confession:
            self.mitigating_factors.append('Priznanje krivice')
            analysis['mitigating_factors'].append('Priznanje krivice (čl. 42)')

        if txt.get('plea_agreement', False):
            self.mitigating_factors.append('Sporazum o priznanju krivice')
            analysis['mitigating_factors'].append('Sporazum o priznanju krivice')

        if txt.get('remorse', False):
            self.mitigating_factors.append('Kajanje/pokajanje')
            analysis['mitigating_factors'].append('Kajanje (čl. 42)')

        if txt.get('restitution', False):
            self.mitigating_factors.append('Naknada štete')
            analysis['mitigating_factors'].append('Naknada/vraćanje štete (čl. 42)')

        if txt.get('has_children', False):
            self.mitigating_factors.append('Porodične prilike - ima decu')
            analysis['mitigating_factors'].append('Porodične prilike - ima decu (čl. 42)')

        employment = txt.get('employment', '')
        if employment and ('nezaposlen' in str(employment).lower()):
            self.mitigating_factors.append('Nezaposlen - loše materijalno stanje')
            analysis['mitigating_factors'].append('Loše materijalno stanje (čl. 42)')

        catch_kg = self.case_facts.get_fact('kolicinaUlovaKg', 0)
        if catch_kg is not None and 0 < catch_kg < 2:
            analysis['mitigating_factors'].append('Mala količina ulova (ispod 2 kg)')

    def _identify_aggravating_factors(self, analysis: Dict):
        if self.case_facts.get_fact('previously_convicted'):
            self.aggravating_factors.append('Ranije osudivan')
            analysis['aggravating_factors'].append('Ranije osudivan')

        zabr = self._fact_is_yes('zabranjenoSredstvo')
        if zabr:
            self.aggravating_factors.append('Korišćenje zabranjenog sredstva')
            analysis['aggravating_factors'].append('Upotreba zabranjenog sredstva za lov/ribolov')

        elektr = self._fact_is_yes('elektricnaStruja')
        if elektr and 'Korišćenje zabranjenog sredstva' not in self.aggravating_factors:
            self.aggravating_factors.append('Korišćenje električne struje')
            analysis['aggravating_factors'].append('Upotreba električne struje za ribolov')

        velika = self._fact_is_yes('velikaKolicina')
        if velika:
            self.aggravating_factors.append('Velika količina ulova')
            analysis['aggravating_factors'].append('Velika količina ulova')

        catch_kg = self.case_facts.get_fact('kolicinaUlovaKg', 0)
        if catch_kg is not None and catch_kg > 10:
            if 'Velika količina ulova' not in self.aggravating_factors:
                self.aggravating_factors.append('Velika količina ulova')
                analysis['aggravating_factors'].append(f'Značajna količina ulova ({catch_kg} kg)')

    def _calculate_recommended_sentence(self, analysis: Dict) -> str:
        penalty_range = analysis['penalty_range']
        if penalty_range['min'] == 'N/A':
            return 'N/A'

        min_years = self._parse_penalty_to_years(penalty_range['min'])
        max_years = self._parse_penalty_to_years(penalty_range['max'])
        if min_years is None or max_years is None:
            return 'N/A'

        num_mitigating = len(analysis['mitigating_factors'])
        num_aggravating = len(analysis['aggravating_factors'])
        txt = self.case_facts.get_fact('txt_facts', {})

        if txt.get('restitution', False) and num_mitigating >= 4 and num_aggravating == 0:
            analysis.setdefault('reasoning_steps', []).append(
                'čl. 47: Razmotreno oslobođenje od kazne (restitucija + 4+ olakšavajućih)'
            )

        ublazavanje_applicable = False
        ublazavanje_reason = ''
        if min_years >= 1:
            if num_mitigating >= 2 and num_aggravating == 0:
                ublazavanje_applicable = True
                ublazavanje_reason = f'Naročito olakšavajuće okolnosti ({num_mitigating} olakšavajućih, 0 otežavajućih) - čl. 45'
            if txt.get('ublazavanje_applied', False):
                ublazavanje_applicable = True
                ublazavanje_reason = 'Sud primenio ublažavanje kazne - čl. 45, 46'

        strong_ublazavanje = num_mitigating >= 3 and num_aggravating == 0
        mitigated_min_years = min_years
        if ublazavanje_applicable:
            if min_years >= 5:
                mitigated_min_years = 2.0
                analysis.setdefault('reasoning_steps', []).append('čl. 46 st. 1 tač. 1: Zakonski minimum ≥5 godina → ublaženo do 2 godine')
            elif min_years >= 3:
                mitigated_min_years = 1.0
                analysis.setdefault('reasoning_steps', []).append('čl. 46 st. 1 tač. 2: Zakonski minimum ≥3 godine → ublaženo do 1 godine')
            elif min_years >= 2:
                mitigated_min_years = 0.5
                analysis.setdefault('reasoning_steps', []).append('čl. 46 st. 1 tač. 3: Zakonski minimum ≥2 godine → ublaženo do 6 meseci')
            elif min_years >= 1:
                mitigated_min_years = 0.25
                analysis.setdefault('reasoning_steps', []).append('čl. 46 st. 1 tač. 4: Zakonski minimum ≥1 godina → ublaženo do 3 meseca')

        if ublazavanje_applicable:
            ub_range = min_years - mitigated_min_years
            rec_years = mitigated_min_years if strong_ublazavanje else mitigated_min_years + (ub_range * 0.15)
            analysis.setdefault('reasoning_steps', []).append(f'Ublažavanje primenjeno: {ublazavanje_reason}')
            analysis.setdefault('reasoning_steps', []).append(
                f'Raspon kazne nakon ublažavanja: {self._format_years(mitigated_min_years)} - {self._format_years(min_years)}'
            )
        else:
            range_span = max_years - min_years
            if num_aggravating > 0 and num_mitigating == 0:
                base_factor = 0.5 if num_aggravating >= 2 else 0.35
            elif num_mitigating > 0 and num_aggravating > 0:
                net = num_aggravating - num_mitigating
                base_factor = max(0.1, min(0.6, 0.25 + (net * 0.1)))
            elif num_mitigating >= 2:
                base_factor = 0.0
                analysis.setdefault('reasoning_steps', []).append('čl. 42: Olakšavajuće okolnosti - kazna na zakonskom minimumu')
            elif num_mitigating == 1:
                base_factor = 0.05
            else:
                base_factor = 0.15

            rec_years = min_years + (range_span * base_factor)
            rec_years = max(min_years, min(max_years, rec_years))

        uslovna_osuda = False
        probation_years = 2
        if rec_years <= 2 and num_mitigating >= 1 and num_aggravating == 0:
            uslovna_osuda = True
            if rec_years <= 0.5:
                probation_years = 1
            elif rec_years <= 1:
                probation_years = 2
            else:
                probation_years = 3
            analysis.setdefault('reasoning_steps', []).append(
                'čl. 52: Uslovna presuda moguća (kazna ≤ 2 godine, olakšavajuće okolnosti)'
            )

        if txt.get('suspended_sentence_articles', False) and rec_years <= 2:
            uslovna_osuda = True

        if uslovna_osuda:
            return f'Uslovna presuda: {self._format_years(rec_years)}, rok provere {probation_years} godine (čl. 52)'
        return self._format_years(rec_years)

    def _parse_penalty_to_years(self, penalty_str: str) -> Optional[float]:
        if isinstance(penalty_str, (int, float)):
            return penalty_str
        penalty_str = str(penalty_str).lower()
        match = re.search(r'(\d+(?:[.,]\d+)?)', penalty_str)
        if not match:
            return None
        num = float(match.group(1).replace(',', '.'))
        if 'dan' in penalty_str:
            return num / 365
        if 'mjesec' in penalty_str or 'mesec' in penalty_str:
            return num / 12
        return num

    def _format_years(self, years: float) -> str:
        days = int(round(years * 365))
        if years < 0.5:
            return "1 dan" if days <= 1 else f"{days} dana"
        if years < 2:
            months = int(round(years * 12))
            return "1 mesec" if months == 1 else f"{months} meseci"
        if years == int(years):
            return "1 godina" if years == 1 else f"{int(years)} godina"
        years_int = int(years)
        months = int((years - years_int) * 12)
        if months == 0:
            return "1 godina" if years_int == 1 else f"{years_int} godina"
        return f"{years_int} godina {months} meseci"

    def _generate_analysis(self, analysis: Dict) -> List[str]:
        lines = []
        case_num = analysis['case_number']
        lines.append(f"Analiza predmeta: {case_num}")

        if analysis['articles']:
            lines.append(f"Primenjeni članci: {', '.join(analysis['articles'])}")

        if analysis['mitigating_factors']:
            lines.append(f"Olakšavajuće okolnosti ({len(analysis['mitigating_factors'])}):")
            for factor in analysis['mitigating_factors']:
                lines.append(f"  - {factor}")

        if analysis['aggravating_factors']:
            lines.append(f"Otežavajuće okolnosti ({len(analysis['aggravating_factors'])}):")
            for factor in analysis['aggravating_factors']:
                lines.append(f"  - {factor}")

        reasoning_steps = analysis.get('reasoning_steps', [])
        if reasoning_steps:
            lines.append("Pravno rezonovanje:")
            for step in reasoning_steps:
                lines.append(f"  → {step}")

        recommendation = analysis['recommendation']
        actual = self.case_facts.get_fact('actual_sentence', 'N/A')
        txt = self.case_facts.get_fact('txt_facts', {})
        actual_text = txt.get('actual_sentence_text', actual)

        if analysis.get('acquittal'):
            lines.append("NAPOMENA: Optuženi je u stvarnom postupku oslobođen od optužbe.")
            lines.append("Rezonovanje po pravilima se ipak primenjuje da bi se videlo šta pravila kažu:")

        if actual_text and actual_text != 'N/A' and recommendation != 'N/A':
            lines.append(f"Preporučena kazna (po pravilima): {recommendation}")
            lines.append(f"Stvarna kazna: {actual_text}")
        elif recommendation != 'N/A':
            lines.append(f"Preporučena kazna (po pravilima): {recommendation}")

        return lines
