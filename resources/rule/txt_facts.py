import re

from pathlib import Path

from rule_shared import PROJECT_ROOT


class TxtFactsExtractor:

    TXT_FOLDERS = [
        'txt/presude/nezakonit ribolov',
        'txt/presude/nezakonit lov',
    ]

    def __init__(self, case_number: str, project_root: Path = None):
        self.case_number = case_number
        self.project_root = project_root or PROJECT_ROOT
        self.txt_text = ""
        self.extracted_facts = {}
        self.load_txt_text()
        if self.txt_text:
            self.extract_all_facts()

    def _normalize_case_number(self, case_num: str) -> str:
        normalized = case_num.strip()
        match = re.search(r'(\d+)[/_](\d+)', normalized)
        if match:
            num = match.group(1)
            year = match.group(2)
            if len(year) == 2:
                year = '20' + year if int(year) < 50 else '19' + year
            return f"K {num}{year}"
        return normalized

    def load_txt_text(self):
        normalized = self._normalize_case_number(self.case_number)
        file_pattern = normalized.replace(" ", "")

        for folder in self.TXT_FOLDERS:
            folder_path = self.project_root / folder
            if folder_path.exists():
                for ext in ['.txt']:
                    file_path = folder_path / f"{file_pattern}{ext}"
                    if file_path.exists():
                        try:
                            self.txt_text = file_path.read_text(encoding='utf-8')
                            self.txt_path = str(file_path)
                            return
                        except Exception:
                            pass

                file_path = folder_path / f"K {file_pattern[1:]}.txt"
                if file_path.exists():
                    try:
                        self.txt_text = file_path.read_text(encoding='utf-8')
                        self.txt_path = str(file_path)
                        return
                    except Exception:
                        pass

    def extract_all_facts(self):
        text = self.txt_text

        match = re.search(r'(?:OSNOVNI SUD U|Osnovni Sud u|OSNOVNI SUD u)\s+([A-ZČĆŽŠĐa-zčćžšđ\s]+?)(?:,|\n)', text, re.IGNORECASE)
        if match:
            self.extracted_facts['court'] = f"Osnovni sud u {match.group(1).strip().title()}"

        match = re.search(r'K\.?(?:br\.?)?\s*(\d+/\d+)', text)
        if match:
            self.extracted_facts['case_number_full'] = f"K {match.group(1)}"

        match = re.search(r'dana\s+(\d{1,2}\.\d{1,2}\.\d{4})\.?\s*g?\.?\s*(?:donio|donijelo)', text)
        if match:
            self.extracted_facts['verdict_date'] = match.group(1)

        judge_patterns = [
            r'sudij[ea],?\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+)',
            r'pojedinac\s+sudij[ea]\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+)',
            r'predsjednik[a]?\s+vijeća\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+)',
        ]
        for pattern in judge_patterns:
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                judge_name = match.group(1).strip()
                if not any(word in judge_name.lower() for word in ['suda', 'sud', 'vijeć']):
                    self.extracted_facts['judge'] = judge_name
                    break

        match = re.search(r'zapisničar[a]?[,:]?\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+)', text, re.IGNORECASE)
        if match:
            self.extracted_facts['court_clerk'] = match.group(1).strip()

        prosecutor_patterns = [
            r'[Tt]užio(?:ca|lac)[,:]?\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+)',
            r'(?:Zamjenik[a]?\s+)?(?:Osnovnog\s+)?(?:državnog\s+)?tužio(?:ca|lac)\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+)',
        ]
        for pattern in prosecutor_patterns:
            match = re.search(pattern, text)
            if match:
                self.extracted_facts['prosecutor'] = match.group(1).strip()
                break

        defense_patterns = [
            r'branioc[a]?[,:]?\s+advokat[a]?\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+)',
            r'advokat[a]?[,:]?\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+)\s*(?:iz|,)',
            r'branioc[a]?[,:]?\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+)\s*(?:iz|,|advokat)',
        ]
        for pattern in defense_patterns:
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                name = match.group(1).strip()
                if not any(word in name.lower() for word in ['okrivljen', 'optužen', 'branio', 'rožaj']):
                    self.extracted_facts['defense_attorney'] = name
                    break

        match = re.search(r'[Oo]ptužen[aio]\s+([A-ZČĆŽŠĐ]\.?\s*[A-ZČĆŽŠĐ]\.?)[,\s]+(?:JMBG)?', text)
        if match:
            self.extracted_facts['defendant_initials'] = match.group(1)

        match = re.search(r'od\s+oca\s+([A-ZČĆŽŠĐa-zčćžšđ\.]+)\s+i\s+majke\s+([A-ZČĆŽŠĐa-zčćžšđ\.]+)', text)
        if match:
            self.extracted_facts['defendant_parents'] = f"otac {match.group(1)}, majka {match.group(2)}"

        match = re.search(r'rođen[a]?\s+[^,]*\s+u\s+([A-ZČĆŽŠĐa-zčćžšđ]+)', text)
        if match:
            self.extracted_facts['birthplace'] = match.group(1)

        match = re.search(r'završi(?:o|la)\s+(osnovnu školu|srednju školu|fakultet|visoku školu|[A-Za-z\s]+školu)', text, re.IGNORECASE)
        if match:
            self.extracted_facts['education'] = match.group(1)

        if re.search(r'nezaposlen[a]?', text, re.IGNORECASE):
            self.extracted_facts['employment'] = 'nezaposlen'
        elif match := re.search(r'zaposlen[a]?\s+(?:kao|u)\s+([^,]+)', text, re.IGNORECASE):
            self.extracted_facts['employment'] = match.group(1).strip()

        if re.search(r'neoženjen|neudata', text, re.IGNORECASE):
            self.extracted_facts['marital_status'] = 'neoženjen/neudata'
        elif re.search(r'oženjen|udata', text, re.IGNORECASE):
            self.extracted_facts['marital_status'] = 'oženjen/udata'

        defendant_block = ''
        defendant_match = re.search(r'(?:O[pk]tužen[aio]|Okrivljen[aio])\s*:?\s*\n?(.{50,800}?)(?:K\s*R\s*I\s*V|OSLOBADJA|OSLOBAĐA|O\s*S\s*U\s*Đ\s*U\s*J\s*E|Zato što)', text, re.DOTALL | re.IGNORECASE)
        if defendant_match:
            defendant_block = defendant_match.group(1)

        if re.search(r'neosudj?ivan[a]?|neosuđivan[a]?|ranije\s+neosudj?ivan|ranije\s+neosuđivan', defendant_block or text, re.IGNORECASE):
            self.extracted_facts['prior_convictions'] = False
            self.extracted_facts['prior_convictions_text'] = 'neosuđivan'
        elif re.search(r'(?:ranije\s+)?osudj?ivan[a]?|(?:ranije\s+)?osuđivan[a]?', defendant_block or text, re.IGNORECASE):
            self.extracted_facts['prior_convictions'] = True
            self.extracted_facts['prior_convictions_text'] = 'osuđivan'
            prior_match = re.search(r'osudj?ivan[a]?\s+(?:i to\s+)?(?:presudom\s+)?(.{10,200}?)(?:\.|,\s*\n)', text, re.IGNORECASE)
            if prior_match:
                self.extracted_facts['prior_conviction_details'] = prior_match.group(1).strip()

        if re.search(r'prizna[ojela]+\s+(?:u cjelosti\s+)?(?:izvršenje\s+)?(?:krivičn|krivic)', text, re.IGNORECASE):
            self.extracted_facts['confession'] = True
        elif re.search(r'prizna[ojela]+\s+(?:svoju\s+)?krivicu', text, re.IGNORECASE):
            self.extracted_facts['confession'] = True
        elif re.search(r'sporazum[a]?\s+o\s+prizn', text, re.IGNORECASE):
            self.extracted_facts['confession'] = True
            self.extracted_facts['plea_agreement'] = True
        else:
            self.extracted_facts['confession'] = False

        if re.search(r'nadoknad[io]+\s+(?:novčan[uo]\s+)?štetu|vratio?\s+novac|nadoknad[io]+.{0,30}oštećen', text, re.IGNORECASE):
            self.extracted_facts['restitution'] = True
        else:
            self.extracted_facts['restitution'] = False

        if re.search(r'izražen[o]?\s+kajanje|iskreno\s+(?:se\s+)?kaje|žao\s+(?:mu|joj|što)', text, re.IGNORECASE):
            self.extracted_facts['remorse'] = True
        else:
            self.extracted_facts['remorse'] = False

        children_match = re.search(r'otac\s+(\w+)\s+(?:maloljetn[eog]+\s+)?(?:dj?ec[ea]|djete)|majka\s+(\w+)\s+(?:maloljetn[eog]+\s+)?(?:dj?ec[ea]|djete)', text, re.IGNORECASE)
        if children_match:
            self.extracted_facts['has_children'] = True
        elif re.search(r'bez\s+djece|nema\s+djece', text, re.IGNORECASE):
            self.extracted_facts['has_children'] = False

        if re.search(r'OSLOBADJA SE OD OPTUŽBE|OSLOBAĐA SE OD OPTUŽBE|oslobadja se od optužbe|OSLOBAĐA SE', text):
            self.extracted_facts['verdict_type'] = 'Oslobađajuća'
            self.extracted_facts['verdict_reason'] = self._extract_acquittal_reason(text)
        elif re.search(r'KRIV\s+JE|K\s*R\s*I\s*V\s*A?\s+JE', text):
            self.extracted_facts['verdict_type'] = 'Osuđujuća'
        elif re.search(r'OGLAŠAVA SE KRIVIM|oglašava se krivim|OSUĐUJE SE', text, re.IGNORECASE):
            self.extracted_facts['verdict_type'] = 'Osuđujuća'

        self._extract_actual_sentence(text)
        self._extract_ublazavanje(text)

        articles = []
        for match in re.finditer(r'čl\.?\s*(\d+)\.?\s*(?:st\.?\s*(\d+))?[\.]?\s*(?:u\s+vezi\s+(?:sa\s+)?(?:st\.?\s*(\d+))?)?\s*(?:Krivičnog zakonika|KZ|KZCG|KZ\s*CG)', text):
            article = f"čl. {match.group(1)}"
            if match.group(2):
                article += f" st. {match.group(2)}"
            if match.group(3):
                article += f" u vezi st. {match.group(3)}"
            if article not in articles:
                articles.append(article)
        if articles:
            self.extracted_facts['criminal_articles'] = articles
            self.extracted_facts['criminal_article'] = articles[0]

        match = re.search(r'(?:zbog\s+)?krivično[g]?\s+djel[ao]?\s+[-–]?\s*([a-zčćžšđ\s]+)\s+iz\s+čl', text, re.IGNORECASE)
        if match:
            self.extracted_facts['crime_type'] = match.group(1).strip()

        match = re.search(r'[Dd]ana\s+(\d{1,2}\.\d{1,2}\.\d{4})\.?\s*(?:g\.?)?\s*(?:oko\s+(\d{1,2}[,:]\d{2})\s*(?:časova|sati)?)?', text)
        if match:
            self.extracted_facts['crime_date'] = match.group(1)
            if match.group(2):
                self.extracted_facts['crime_time'] = match.group(2).replace(',', ':')

        location_patterns = [
            r'na\s+(Skadarskom?\s+jezeru?|S\.\s*j\.?)',
            r'na\s+rijeci\s+([A-ZČĆŽŠĐa-zčćžšđ\s]+?)(?:,|\s+u)',
            r'na\s+jezeru\s+([A-ZČĆŽŠĐa-zčćžšđ\s]+?)(?:,|\s+u)',
            r'u\s+lovištu\s+([^,\.]+)',
            r'u\s+mjestu\s+([A-ZČĆŽŠĐa-zčćžšđ]+)',
        ]
        for pattern in location_patterns:
            match = re.search(pattern, text, re.IGNORECASE)
            if match:
                self.extracted_facts['crime_location_name'] = match.group(1).strip()
                break

        self.extracted_facts['electric_current'] = bool(
            re.search(r'elektri[čc]n[aou]+\s+struj[aou]|lovili?\s+ribu\s+elektri[čc]nom', text, re.IGNORECASE)
        )
        self.extracted_facts['aggregate'] = bool(re.search(r'agregat[a]?', text, re.IGNORECASE))
        self.extracted_facts['probe'] = bool(re.search(r'sond[aeu]', text, re.IGNORECASE))
        self.extracted_facts['converter'] = bool(re.search(r'pretvar[aou][čc]', text, re.IGNORECASE))
        self.extracted_facts['prohibited_means'] = bool(
            re.search(r'zabranj?en[aio]+\s+sredstv[aio]|nedozvolj?en[aio]+\s+sredstv[aio]', text, re.IGNORECASE)
        )

        catch_match = re.search(r'ulovi(?:li|o|la)?\s+(\d+)\s+(?:komad[a]?\s+)?(?:rib[aeu]|som[aou]|šaran[aou]|ljolje?|cipole?|krap[aou]|ukljeve?)', text, re.IGNORECASE)
        if catch_match:
            self.extracted_facts['catch_present'] = True
            self.extracted_facts['catch_count'] = int(catch_match.group(1))
        elif re.search(r'ulov(?:ljen|io|ila|ili|[a]?)\s+rib[eu]|u\s+čunu.*rib[ae]|zatekli?\s+ulov', text, re.IGNORECASE):
            self.extracted_facts['catch_present'] = True

        kg_match = re.search(r'(\d+[,.]?\d*)\s*(?:kg|kilograma?)', text, re.IGNORECASE)
        if kg_match:
            self.extracted_facts['catch_quantity_kg'] = float(kg_match.group(1).replace(',', '.'))

        self.extracted_facts['closed_season_or_prohibited'] = bool(
            re.search(r'lovosta[ju]|zabranj?en[aio]+\s+(?:vode?|akvatorij|period)|u\s+vrijeme\s+zabrane', text, re.IGNORECASE)
        )
        self.extracted_facts['co_perpetration'] = bool(re.search(r'saizvrši(?:laštv|oc)', text, re.IGNORECASE))
        self.extracted_facts['confiscation'] = bool(
            re.search(r'oduzim[ae]\s+(?:se\s+)?(?:predmet|sredstv)|oduzetim?\s+predmet', text, re.IGNORECASE)
        )
        self.extracted_facts['large_quantity'] = bool(
            re.search(r'velik[aou]+\s+količ?in[aou]|značajn[aou]+\s+količ?in[aou]', text, re.IGNORECASE)
        )

        witnesses = []
        witness_patterns = [
            r'(?:svjedok|svedok)[a]?(?:\s*[-–]\s*oštećen[aio]g?)?[,:]?\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\.?\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+\.?)',
            r'(?:svjedok|svedok)[a]?(?:\s*[-–]\s*oštećen[aio]g?)?[,:]?\s+([A-ZČĆŽŠĐ]\.?\s*[A-ZČĆŽŠĐ]\.?)',
            r'saslušan[aio]?\s+(?:svjedok|svedok)[a]?\s+([A-ZČĆŽŠĐ][a-zčćžšđ]+\.?\s+[A-ZČĆŽŠĐ][a-zčćžšđ]+\.?)',
        ]
        for pattern in witness_patterns:
            for match in re.finditer(pattern, text, re.IGNORECASE):
                witness = match.group(1).strip()
                if witness and witness not in witnesses and len(witness) > 2:
                    if not any(word in witness.lower() for word in ['sud', 'vijeć', 'optužen', 'okrivlj', 'tužio', 'branioc']):
                        witnesses.append(witness)
        if witnesses:
            self.extracted_facts['witnesses'] = witnesses

        evidence = []
        evidence_patterns = [
            r'(potvrdu o privremeno oduzetim predmetima[^,\.]+)',
            r'(izvještaj[a]?\s+(?:kriminalističke tehnike|tehničke[^,\.]+))',
            r'(nalaz[a]?\s+i\s+mišljenj[ae]\s+vještaka[^,\.]+)',
            r'(zapisnik[a]?\s+o\s+(?:izvršenoj\s+)?kontroli[^,\.]+)',
            r'(zapisnik[a]?\s+o\s+prepoznavanju[^,\.]+)',
            r'(izvod[a]?\s+iz\s+registra\s+kaznenih\s+evidencija[^,\.]+)',
        ]
        for pattern in evidence_patterns:
            for match in re.finditer(pattern, text, re.IGNORECASE):
                evidence_item = match.group(1).strip()
                if evidence_item and evidence_item not in evidence:
                    evidence.append(evidence_item)
        if evidence:
            self.extracted_facts['evidence_types'] = evidence

        if re.search(r'in\s+dubio\s+pro\s+reo', text, re.IGNORECASE):
            self.extracted_facts['legal_principle'] = 'in dubio pro reo (u slučaju sumnje u korist optuženog)'

        match = re.search(r'[Tt]roškovi[^\.]*?(?:iznos[u]?\s+(?:od\s+)?)?(\d+[,\.]\d+|\d+)\s*(?:eura?|EUR)', text)
        if match:
            self.extracted_facts['court_costs'] = f"{match.group(1)} EUR"

        self.extracted_facts['case_summary'] = self._extract_case_summary(text)

    def _extract_actual_sentence(self, text: str):
        if re.search(r'USLOVN[UAO]\s+OSUD[UAO]', text, re.IGNORECASE):
            self.extracted_facts['sentence_type'] = 'uslovna_osuda'
            match = re.search(r'(?:utvr[đd]uje)?(?:\s+joj|\s+mu|\s+im)?\s*kazn[aue]\s+zatvora\s+u\s+trajanju\s+od\s+(?:po\s+)?(\d+)\s*\(?(\w+)\)?', text, re.IGNORECASE)
            if match:
                num = int(match.group(1))
                unit = match.group(2).lower()
                if 'mjesec' in unit or 'mesec' in unit:
                    self.extracted_facts['sentence_months'] = num
                    self.extracted_facts['actual_sentence_text'] = f"Uslovna presuda: {num} meseci zatvora"
                elif 'godin' in unit or 'leto' in unit:
                    self.extracted_facts['sentence_months'] = num * 12
                    self.extracted_facts['actual_sentence_text'] = f"Uslovna presuda: {num} god. zatvora"
                elif 'dan' in unit:
                    self.extracted_facts['sentence_days'] = num
                    self.extracted_facts['actual_sentence_text'] = f"Uslovna presuda: {num} dana zatvora"
            else:
                self.extracted_facts['actual_sentence_text'] = 'Uslovna presuda'

            probation_match = re.search(r'(?:rok[u]?\s+(?:od\s+)?|u\s+roku\s+od\s+)(\d+)\s*\(?(\w+)\)?\s*(?:,|\s+)?(?:po\s+pravnosna|ne\s+u[čc]ini|od\s+dana)', text, re.IGNORECASE)
            if probation_match:
                prob_num = int(probation_match.group(1))
                prob_unit = probation_match.group(2).lower()
                if 'godin' in prob_unit:
                    self.extracted_facts['probation_years'] = prob_num
                    previous = self.extracted_facts.get('actual_sentence_text', 'Uslovna presuda')
                    self.extracted_facts['actual_sentence_text'] = f"{previous}, rok provere {prob_num} godine"

        elif re.search(r'O\s*S\s*U\s*[ĐD]\s*U\s*J\s*E', text):
            match = re.search(r'(?:Na\s+)?kazn[aue]\s+zatvora\s+u\s+trajanju\s+od\s+(\d+)\s*\(?(\w+)\)?', text, re.IGNORECASE)
            if match:
                num = int(match.group(1))
                unit = match.group(2).lower()
                if 'mjesec' in unit or 'mesec' in unit:
                    self.extracted_facts['sentence_type'] = 'efektivni_zatvor'
                    self.extracted_facts['sentence_months'] = num
                    self.extracted_facts['actual_sentence_text'] = f"{num} meseci zatvora"
                elif 'godin' in unit:
                    self.extracted_facts['sentence_type'] = 'efektivni_zatvor'
                    self.extracted_facts['sentence_months'] = num * 12
                    self.extracted_facts['actual_sentence_text'] = f"{num} godina zatvora"
                elif 'dan' in unit:
                    self.extracted_facts['sentence_type'] = 'efektivni_zatvor'
                    self.extracted_facts['sentence_days'] = num
                    self.extracted_facts['actual_sentence_text'] = f"{num} dana zatvora"

        if 'actual_sentence_text' not in self.extracted_facts:
            match = re.search(r'kazn[aue]\s+zatvora\s+u\s+trajanju\s+od\s+(?:po\s+)?(\d+)\s*\(?(\w+)\)?', text, re.IGNORECASE)
            if match:
                num = int(match.group(1))
                unit = match.group(2).lower()
                if 'mjesec' in unit or 'mesec' in unit:
                    self.extracted_facts['actual_sentence_text'] = f"{num} meseci zatvora"
                    self.extracted_facts['sentence_months'] = num
                elif 'godin' in unit:
                    self.extracted_facts['actual_sentence_text'] = f"{num} godina zatvora"
                    self.extracted_facts['sentence_months'] = num * 12
                elif 'dan' in unit:
                    self.extracted_facts['actual_sentence_text'] = f"{num} dana zatvora"
                    self.extracted_facts['sentence_days'] = num

        match = re.search(r'rad[a]?\s+u\s+javnom\s+interesu\s+(?:u\s+trajanju\s+od\s+)?(\d+)\s*(?:časov[a]?|sati)', text, re.IGNORECASE)
        if match:
            self.extracted_facts['public_interest_work_hours'] = int(match.group(1))

        if re.search(r'MJERA\s+BEZBJEDNOSTI|mjera\s+bezbjednosti|MERA\s+BEZBEDNOSTI|mera\s+bezbednosti', text, re.IGNORECASE):
            self.extracted_facts['security_measure'] = True
            match = re.search(r'(?:Oduzimanj[ae]|oduzimanj[ae])\s+(.{10,100}?)(?:\.|,)', text)
            if match:
                self.extracted_facts['security_measure_detail'] = match.group(1).strip()

    def _extract_ublazavanje(self, text: str):
        if re.search(r'ubla[žz]avan[je]+\s+kazne|ubla[žz]i[ot]?\s+kazn|ubla[žz]en[aou]?\s+kazn', text, re.IGNORECASE):
            self.extracted_facts['ublazavanje_applied'] = True

        if re.search(r'[čc]l\.?\s*45', text):
            self.extracted_facts['ublazavanje_applied'] = True
            self.extracted_facts['ublazavanje_cl45'] = True

        if re.search(r'[čc]l\.?\s*46\s*(?:st\.?\s*(\d+))?\s*(?:ta[čc]\.?\s*(\d+))?', text):
            self.extracted_facts['ublazavanje_applied'] = True
            match = re.search(r'[čc]l\.?\s*46\s+st\.?\s*(\d+)\s+ta[čc]\.?\s*(\d+)', text)
            if match:
                self.extracted_facts['ublazavanje_cl46_st'] = int(match.group(1))
                self.extracted_facts['ublazavanje_cl46_tac'] = int(match.group(2))

        if re.search(r'naročito\s+olakšavajuć|naro[čc]ito\s+olak[šs]avaju[ćc]', text, re.IGNORECASE):
            self.extracted_facts['especially_mitigating'] = True
            self.extracted_facts['ublazavanje_applied'] = True

        applied_articles = []
        for match in re.finditer(r'(?:primjenom\s+)?[čc]l\.?\s*(\d+)', text):
            art_num = int(match.group(1))
            if art_num in [42, 45, 46, 47, 48, 49, 52, 53, 54]:
                applied_articles.append(art_num)
        if applied_articles:
            self.extracted_facts['sentencing_articles_applied'] = list(set(applied_articles))

        if 52 in applied_articles or 53 in applied_articles or 54 in applied_articles:
            self.extracted_facts['suspended_sentence_articles'] = True

    def _extract_acquittal_reason(self, text: str) -> str:
        if re.search(r'nije dokazano da je izvršil[ao]', text, re.IGNORECASE):
            return 'Nije dokazano da je optuženi izvršio delo za koje je optužen'
        if re.search(r'djelo.+nije krivično djelo', text, re.IGNORECASE):
            return 'Delo za koje je optužen nije krivično delo'
        if re.search(r'nema dovoljno dokaza', text, re.IGNORECASE):
            return 'Nema dovoljno dokaza'
        return 'Primjena načela in dubio pro reo'

    def _extract_case_summary(self, text: str) -> str:
        match = re.search(r'Da je,\s*\n\s*(.{100,500}?)(?:\n\n|-čime)', text, re.DOTALL)
        if match:
            summary = match.group(1).strip()
            summary = re.sub(r'\s+', ' ', summary)
            return summary[:300] + '...' if len(summary) > 300 else summary
        return ""

    def get_fact(self, key: str, default=None):
        return self.extracted_facts.get(key, default)

    def has_txt(self) -> bool:
        return bool(self.txt_text)
