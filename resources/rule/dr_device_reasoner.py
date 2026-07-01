import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

from pathlib import Path
from typing import Dict, List, Tuple

from rule_shared import PROJECT_ROOT


class DrDeviceReasoner:

    DR_DEVICE_DIR = PROJECT_ROOT / 'rule' / 'dr-device'
    LC_NS = 'http://informatika.ftn.uns.ac.rs/legal-case.rdf#'

    def __init__(self, case_facts_dict: Dict):
        self.facts = case_facts_dict
        self.dr_device_dir = self.DR_DEVICE_DIR

    def _map_article(self) -> str:
        articles = self.facts.get('articles', [])
        for article in articles:
            if '326' in str(article):
                return '326'
            if '325' in str(article):
                return '325'
        crime_type = self.facts.get('crime_type', '')
        if 'ribolov' in crime_type.lower():
            return '326'
        if 'lov' in crime_type.lower():
            return '325'
        return ''

    def _bool_to_yesno(self, val) -> str:
        if val is True:
            return 'yes'
        if val is False or val is None:
            return 'no'
        val_str = str(val).lower().strip()
        if val_str in ('da', 'yes', 'true', '1'):
            return 'yes'
        return 'no'

    def generate_facts_rdf(self) -> str:
        article = self._map_article()
        if not article:
            return ''

        txt = self.facts.get('txt_facts', {})
        fish_closed = self._bool_to_yesno(self.facts.get('lovostajIliZabranjeneVode', txt.get('closed_season_or_prohibited')))
        fish_prohibited_means = self._bool_to_yesno(self.facts.get('zabranjenoSredstvo', txt.get('prohibited_means')))
        fish_large_quantity = self._bool_to_yesno(self.facts.get('velikaKolicina', txt.get('large_quantity')))
        use_electricity = self._bool_to_yesno(self.facts.get('elektricnaStruja', txt.get('electric_current')))
        use_aggregate = self._bool_to_yesno(self.facts.get('agregat', txt.get('aggregate')))
        use_probe = self._bool_to_yesno(self.facts.get('sonda', txt.get('probe')))
        use_converter = self._bool_to_yesno(self.facts.get('pretvarac', txt.get('converter')))
        catch_present = self._bool_to_yesno(self.facts.get('prisutanUlov', txt.get('catch_present')))
        confiscation = self._bool_to_yesno(self.facts.get('oduzimanjePredmeta', txt.get('confiscation')))

        hunt_closed = self._bool_to_yesno(self.facts.get('lovostajIliZabranjeneVode', txt.get('closed_season_or_prohibited')))
        hunt_foreign = self._bool_to_yesno(self.facts.get('hunt_foreign_hunting_ground', 'no'))
        hunt_killed = self._bool_to_yesno(self.facts.get('hunt_killed_injured_or_captured_game', 'no'))
        hunt_big_game = self._bool_to_yesno(self.facts.get('hunt_big_game', 'no'))
        hunt_prohibited_species = self._bool_to_yesno(self.facts.get('hunt_prohibited_species', 'no'))
        hunt_without_permit = self._bool_to_yesno(self.facts.get('hunt_without_special_permit', 'no'))
        hunt_mass_destruction = self._bool_to_yesno(self.facts.get('hunt_mass_destruction_means', 'no'))

        co_perpetration = self._bool_to_yesno(self.facts.get('saizvrsilastvo', txt.get('co_perpetration')))
        cited_23_2 = co_perpetration
        repeat_offender = self._bool_to_yesno(self.facts.get('previously_convicted'))

        return f'''<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
         xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
         xmlns:lc="http://informatika.ftn.uns.ac.rs/legal-case.rdf#">
  <lc:case rdf:about="http://informatika.ftn.uns.ac.rs/legal-case.rdf#case01">
    <lc:co_perpetration>{co_perpetration}</lc:co_perpetration>
    <lc:cited_article_23_para_2>{cited_23_2}</lc:cited_article_23_para_2>
    <lc:fish_closed_season_or_prohibited_waters>{fish_closed}</lc:fish_closed_season_or_prohibited_waters>
    <lc:fish_prohibited_means>{fish_prohibited_means}</lc:fish_prohibited_means>
    <lc:fish_large_quantity_or_high_value>{fish_large_quantity}</lc:fish_large_quantity_or_high_value>
    <lc:use_of_electricity>{use_electricity}</lc:use_of_electricity>
    <lc:use_of_aggregate>{use_aggregate}</lc:use_of_aggregate>
    <lc:use_of_probe>{use_probe}</lc:use_of_probe>
    <lc:use_of_converter>{use_converter}</lc:use_of_converter>
    <lc:catch_present>{catch_present}</lc:catch_present>
    <lc:confiscation_ordered>{confiscation}</lc:confiscation_ordered>
    <lc:hunt_closed_season_or_prohibited_area>{hunt_closed}</lc:hunt_closed_season_or_prohibited_area>
    <lc:hunt_foreign_hunting_ground>{hunt_foreign}</lc:hunt_foreign_hunting_ground>
    <lc:hunt_killed_injured_or_captured_game>{hunt_killed}</lc:hunt_killed_injured_or_captured_game>
    <lc:hunt_big_game>{hunt_big_game}</lc:hunt_big_game>
    <lc:hunt_prohibited_species>{hunt_prohibited_species}</lc:hunt_prohibited_species>
    <lc:hunt_without_special_permit>{hunt_without_permit}</lc:hunt_without_special_permit>
    <lc:hunt_mass_destruction_means>{hunt_mass_destruction}</lc:hunt_mass_destruction_means>
    <lc:repeat_offender>{repeat_offender}</lc:repeat_offender>
  </lc:case>
</rdf:RDF>'''

    def _rdf_to_n3(self, rdf_content: str) -> str:
        lines = []
        rdf_ns = 'http://www.w3.org/1999/02/22-rdf-syntax-ns#'

        try:
            root = ET.fromstring(rdf_content)
        except ET.ParseError:
            return ''

        for elem in root:
            tag = elem.tag
            if '}' not in tag:
                continue
            ns, local = tag.split('}', 1)
            ns = ns[1:]

            about = elem.get(f'{{{rdf_ns}}}about', '')
            if not about:
                continue

            lines.append(f'<{about}> <{rdf_ns}type> <{ns}{local}> .')

            for prop in elem:
                ptag = prop.tag
                if '}' not in ptag:
                    continue
                pns, plocal = ptag.split('}', 1)
                pns = pns[1:]
                text = (prop.text or '').strip()
                if text:
                    lines.append(f'<{about}> <{pns}{plocal}> "{text}" .')

        return '\n'.join(lines) + '\n'

    def run_clipsdos(self) -> Tuple[bool, str]:
        clips = self.dr_device_dir / 'CLIPSDOS' / 'CLIPSDOS.exe'
        if not clips.exists():
            return False, f'CLIPSDOS.exe not found at {clips}'

        try:
            command = [str(clips), '-f2', 'start.clp']
            if os.name != 'nt':
                command = ['wine', str(clips), '-f2', 'start.clp']

            result = subprocess.run(
                command,
                cwd=str(self.dr_device_dir),
                capture_output=True,
                text=True,
                timeout=30,
                encoding='utf-8',
                errors='replace',
            )
            return result.returncode == 0, result.stdout
        except subprocess.TimeoutExpired:
            return False, 'DR-Device timed out (30s)'
        except FileNotFoundError as error:
            if os.name != 'nt' and error.filename == 'wine':
                return False, 'wine is not installed or not available in PATH'
            return False, str(error)
        except Exception as error:
            return False, str(error)

    def parse_export_rdf(self) -> Dict:
        export_path = self.dr_device_dir / 'export.rdf'
        if not export_path.exists():
            return {'error': 'No export.rdf generated'}

        content = export_path.read_text(encoding='utf-8', errors='replace')
        results = {
            'proven_conclusions': [],
            'guilt': None,
            'guilt_label': None,
            'has_mitigating': False,
            'has_aggravating': False,
            'mitigation_allowed': False,
            'suspended_sentence_possible': False,
            'penalty_max_days': None,
            'confiscation_required': False,
            'joint_perpetration': False,
        }

        blocks = re.findall(r'<export:(\w+)\s+rdf:about=[^>]+>(.*?)</export:\1>', content, re.DOTALL)
        guilt_labels = {
            'illegal_hunting_para_1': 'Čl. 325 st. 1 (lov u lovostaju ili zabranjenom području)',
            'illegal_hunting_para_2': 'Čl. 325 st. 2 (lov u tuđem lovištu uz ubijanje/hvatanje divljači)',
            'illegal_hunting_para_3': 'Čl. 325 st. 3 (lov u tuđem lovištu - krupna divljač)',
            'illegal_hunting_para_4': 'Čl. 325 st. 4 (zaštićena vrsta / bez dozvole / sredstva za masovno uništavanje)',
            'illegal_fishing_para_1': 'Čl. 326 st. 1 (ribolov u lovostaju ili zabranjenim vodama)',
            'illegal_fishing_para_2': 'Čl. 326 st. 2 (ribolov zabranjenim sredstvima)',
            'illegal_fishing_para_3': 'Čl. 326 st. 3 (velika količina ili velika vrijednost ulova)',
        }

        for class_name, block_content in blocks:
            truth = re.search(r'truthStatus[^>]*>([^<]+)', block_content)
            if truth and 'proven-positive' in truth.group(1):
                results['proven_conclusions'].append(class_name)
                if class_name.startswith('illegal_hunting_') or class_name.startswith('illegal_fishing_'):
                    results['guilt'] = class_name
                    results['guilt_label'] = guilt_labels.get(class_name, class_name)
                elif class_name == 'has_mitigating':
                    results['has_mitigating'] = True
                elif class_name == 'has_aggravating':
                    results['has_aggravating'] = True
                elif class_name == 'mitigation_allowed':
                    results['mitigation_allowed'] = True
                elif class_name == 'suspended_sentence_possible':
                    results['suspended_sentence_possible'] = True
                elif class_name == 'joint_perpetration':
                    results['joint_perpetration'] = True
                elif class_name.startswith('confiscation_'):
                    results['confiscation_required'] = True
                elif class_name == 'to_imprison_max':
                    val = re.search(r'value[^>]*>(\d+)', block_content)
                    if val:
                        results['penalty_max_days'] = int(val.group(1))
        return results

    def parse_proof_ruleml(self) -> List[Dict]:
        proof_path = self.dr_device_dir / 'proof.ruleml'
        if not proof_path.exists():
            return []

        content = proof_path.read_text(encoding='utf-8', errors='replace')
        proofs = []
        proved_blocks = re.findall(r'<Defeasibly_Proved>(.*?)</Defeasibly_Proved>', content, re.DOTALL)

        for block in proved_blocks:
            proof = {}
            resource = re.search(r'RDF_resource\s+uri=[\'"]([^"\']+)', block)
            if resource:
                uri = resource.group(1).split(';')[-1] if ';' in resource.group(1) else resource.group(1)
                proof['conclusion'] = uri.rstrip("'\"")

            rule_ref = re.search(r'supportive_rule.*?rule=[\'"](\w+)', block, re.DOTALL)
            if rule_ref:
                proof['rule'] = rule_ref.group(1)

            if '<Blocked>' in block:
                proof['had_blocked_attackers'] = True
                blocked_rule = re.search(r'Blocked_Defeasible_rule.*?rule=[\'"](\w+)', block, re.DOTALL)
                if blocked_rule:
                    proof['blocked_rule'] = blocked_rule.group(1)

            if proof:
                proofs.append(proof)
        return proofs

    def run(self) -> Dict:
        rdf = self.generate_facts_rdf()
        if not rdf:
            return {'status': 'skipped', 'reason': 'Nije pronadjen primjenjivi član (325/326) za DR-Device'}

        (self.dr_device_dir / 'facts.rdf').write_text(rdf, encoding='utf-8')
        n3 = self._rdf_to_n3(rdf)
        (self.dr_device_dir / 'facts.n3').write_text(n3, encoding='utf-8')

        clp_path = self.dr_device_dir / 'rulebase.clp'
        if not clp_path.exists():
            self._ensure_rulebase()

        success, output = self.run_clipsdos()
        if not success:
            return {'status': 'error', 'reason': f'CLIPSDOS failed: {output[:500]}'}

        results = self.parse_export_rdf()
        results['status'] = 'success'
        results['proof_chain'] = self.parse_proof_ruleml()
        results['summary'] = self._format_summary(results)
        return results

    def _ensure_rulebase(self):
        try:
            import importlib.util

            spec = importlib.util.spec_from_file_location(
                "transform_lrml",
                str(self.dr_device_dir / "transform_lrml.py"),
            )
            mod = importlib.util.module_from_spec(spec)  # type: ignore[arg-type]
            spec.loader.exec_module(mod)  # type: ignore[union-attr]

            lrml_path = self.dr_device_dir / 'rulebase.lrml'
            ruleml_path = self.dr_device_dir / 'rulebase.ruleml'
            clp_path = self.dr_device_dir / 'rulebase.clp'

            transformer = mod.LrmlToRulemlTransformer(str(lrml_path), 'facts.rdf')
            ruleml_content = transformer.transform()
            ruleml_path.write_text(ruleml_content, encoding='utf-8')

            clips_transformer = mod.RulemlToClipsTransformer(str(ruleml_path))
            clips_content = clips_transformer.transform()
            clp_path.write_text(clips_content, encoding='utf-8')
        except Exception as error:
            print(f"Warning: Could not generate rulebase.clp: {error}", file=sys.stderr)

    def _format_summary(self, results: Dict) -> str:
        lines = []
        if results.get('guilt_label'):
            lines.append(f"Krivica: {results['guilt_label']}")
        if results.get('joint_perpetration'):
            lines.append("Saizvršilaštvo: utvrđeno (čl. 23 st. 2)")
        if results.get('has_mitigating'):
            lines.append("Olakšavajuće okolnosti: utvrđene (defeasibly proven)")
        if results.get('has_aggravating'):
            lines.append("Otežavajuće okolnosti: utvrđene (defeasibly proven)")
        if results.get('mitigation_allowed'):
            lines.append("Ublažavanje kazne: dozvoljeno (čl. 45 KZ CG)")
        if results.get('suspended_sentence_possible'):
            lines.append("Uslovna presuda: moguća (čl. 52 KZ CG)")
        if results.get('confiscation_required'):
            lines.append("Oduzimanje predmeta: obavezno")

        max_d = results.get('penalty_max_days')
        if max_d is not None:
            lines.append(f"Maksimalna kazna zatvora: {max_d} dana")
        return '; '.join(lines) if lines else 'Nema zaključaka'
