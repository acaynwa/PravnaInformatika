import re
import sys

from pathlib import Path

from txt_facts import TxtFactsExtractor


class CaseFactsExtractor:

    def __init__(self, case_file: str):
        self.case_file = case_file
        self.facts = {}
        self.txt_extractor = None
        self.extract_facts()
        self.load_txt_facts()

    def extract_facts(self):
        try:
            content = Path(self.case_file).read_text(encoding='utf-8')

            match = re.search(r'<brojPredmeta>([^<]+)</brojPredmeta>', content)
            if match:
                self.facts['case_number'] = match.group(1).strip()

            match = re.search(r'<vrstaPresude>([^<]+)</vrstaPresude>', content)
            if match:
                self.facts['verdict'] = match.group(1).strip()

            match = re.search(r'<kazna>([^<]+)</kazna>', content)
            if match:
                self.facts['actual_sentence'] = match.group(1).strip()

            articles = []
            for match in re.finditer(r'<clanKZ>([^<]+)</clanKZ>', content):
                article = match.group(1).strip()
                if article and article not in articles:
                    articles.append(article)
            for match in re.finditer(r'href="/krivicni#art_(\d+)_para_(\d+)"', content):
                article = f"{match.group(1)}.{match.group(2)}"
                if article not in articles:
                    articles.append(article)
            self.facts['articles'] = articles

            match = re.search(r'<sudija>([^<]+)</sudija>', content)
            if match:
                self.facts['judge'] = match.group(1).strip()

            match = re.search(r'<zapisnicar>([^<]+)</zapisnicar>', content)
            if match:
                self.facts['court_clerk'] = match.group(1).strip()

            match = re.search(r'<optuzeni>([^<]+)</optuzeni>', content)
            if match:
                self.facts['defendant'] = match.group(1).strip()

            for match in re.finditer(r'eId="defendant_\d+"[^>]*showAs="([^"]+)"', content):
                if 'defendant' not in self.facts:
                    self.facts['defendant'] = match.group(1).strip()

            match = re.search(r'<tipKrivicnogDjela>([^<]+)</tipKrivicnogDjela>', content)
            if match:
                self.facts['crime_type'] = match.group(1).strip()
            if 'crime_type' not in self.facts:
                if re.search(r'nezakonit\w*\s+ribolov', content, re.IGNORECASE):
                    self.facts['crime_type'] = 'nezakonit ribolov'
                elif re.search(r'nezakonit\w*\s+lov', content, re.IGNORECASE):
                    self.facts['crime_type'] = 'nezakonit lov'

            match = re.search(r'<sud>([^<]+)</sud>', content)
            if match:
                raw_court = match.group(1).strip()
                raw_court = re.sub(r'\s+po\s+sudij.*$', '', raw_court, flags=re.IGNORECASE).strip()
                court_name = raw_court.title()
                if not court_name.lower().startswith('osnovni'):
                    court_name = f"Osnovni sud u {court_name}"
                self.facts['court'] = court_name
            if 'court' not in self.facts:
                match = re.search(r'showAs="([^"]*(?:Osnovni|Sud)[^"]*)"', content)
                if match:
                    self.facts['court'] = match.group(1).strip()

            match = re.search(r'<datum>([^<]+)</datum>', content)
            if match:
                self.facts['date'] = match.group(1).strip()
            if 'date' not in self.facts:
                match = re.search(r'FRBRdate\s+date="([^"]+)"', content)
                if match:
                    self.facts['date'] = match.group(1).strip()

            match = re.search(r'<opisSlucaja>([^<]+)</opisSlucaja>', content)
            if match:
                self.facts['case_description'] = match.group(1).strip()

            witnesses = []
            for match in re.finditer(r'<svjedok>([^<]+)</svjedok>', content):
                witness = match.group(1).strip()
                if witness and witness not in witnesses:
                    witnesses.append(witness)
            self.facts['witnesses'] = witnesses

            evidence = []
            for match in re.finditer(r'<dokaz>([^<]+)</dokaz>', content):
                evidence_item = match.group(1).strip()
                if evidence_item and evidence_item not in evidence:
                    evidence.append(evidence_item)
            self.facts['evidence'] = evidence

            for tag in [
                'zabranjenoSredstvo',
                'lovostajIliZabranjeneVode',
                'velikaKolicina',
                'elektricnaStruja',
                'agregat',
                'sonda',
                'pretvarac',
                'prisutanUlov',
                'oduzimanjePredmeta',
                'saizvrsilastvo',
            ]:
                match = re.search(rf'<{tag}>([^<]*)</{tag}>', content)
                if match:
                    self.facts[tag] = match.group(1).strip()

            match = re.search(r'<kolicinaUlovaKg>([^<]*)</kolicinaUlovaKg>', content)
            if match:
                try:
                    self.facts['kolicinaUlovaKg'] = float(match.group(1).strip().replace(',', '.'))
                except ValueError:
                    self.facts['kolicinaUlovaKg'] = 0.0

            match = re.search(r'<ranijeOsudjivan>([^<]*)</ranijeOsudjivan>', content)
            if match:
                value = match.group(1).strip().lower()
                self.facts['previously_convicted'] = value in ['da', 'yes', 'true']
                self.facts['prior_convictions_text'] = match.group(1).strip()
            else:
                self.facts['previously_convicted'] = False

            match = re.search(r'<uslovnaOsuda>([^<]*)</uslovnaOsuda>', content)
            self.facts['conditional_sentence'] = bool(
                match and match.group(1).strip().lower() in ['da', 'yes', 'true']
            )

            match = re.search(r'<godina>(\d{4})</godina>', content)
            if match:
                self.facts['year'] = int(match.group(1))

        except Exception as error:
            print(f"Error extracting facts: {error}", file=sys.stderr)

    def load_txt_facts(self):
        case_num = self.facts.get('case_number', '')
        if case_num:
            self.txt_extractor = TxtFactsExtractor(case_num)
            if self.txt_extractor.has_txt():
                for key, value in self.txt_extractor.extracted_facts.items():
                    if key not in self.facts or not self.facts[key]:
                        self.facts[key] = value
                    elif key == 'witnesses' and isinstance(value, list):
                        existing = self.facts.get('witnesses', [])
                        for witness in value:
                            if witness not in existing:
                                existing.append(witness)
                        self.facts['witnesses'] = existing
                self.facts['txt_facts'] = self.txt_extractor.extracted_facts

    def get_fact(self, key: str, default=None):
        return self.facts.get(key, default)

    def has_article(self, article_pattern: str) -> bool:
        articles = self.facts.get('articles', [])
        for article in articles:
            if article_pattern.lower() in article.lower():
                return True
        return False

    def has_txt(self) -> bool:
        return self.txt_extractor is not None and self.txt_extractor.has_txt()
