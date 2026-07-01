package connector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedList;

import es.ucm.fdi.gaia.jcolibri.cbrcore.CBRCase;
import es.ucm.fdi.gaia.jcolibri.cbrcore.CaseBaseFilter;
import es.ucm.fdi.gaia.jcolibri.cbrcore.Connector;
import es.ucm.fdi.gaia.jcolibri.exception.InitializingException;
import model.CaseDescription;

/**
 * Cita slucajeve iz CSV fajla (presude.csv) koji je generisan
 * ekstrahovanjem podataka iz Akomanotoso XML dokumenata.
 *
 * Format CSV-a (separator: ;):
 * id;sud;brojPredmeta;tipKrivicnogDjela;clanKZ;saizvrsilastvo;zabranjenoSredstvo;
 * lovostajIliZabranjeneVode;velikaKolicina;elektricnaStruja;agregat;sonda;pretvarac;
 * prisutanUlov;kolicinaUlovaKg;oduzimanjePredmeta;ranijeOsudjivan;kaznaZatvoraMjeseci;
 * kaznaZatvoraDani;radUJavnomInteresuCasovi;rokProvjereGodine;troskoviEur;vrstaPresude;
 * uslovnaOsuda;brojOkrivljenih;brojSvjedoka
 */
public class CsvConnector implements Connector {

    @Override
    public Collection<CBRCase> retrieveAllCases() {
        LinkedList<CBRCase> cases = new LinkedList<>();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(getClass().getResourceAsStream("/presude.csv"), "UTF-8"))) {

            String line;
            while ((line = br.readLine()) != null) {
                // Skip comments and empty lines
                if (line.startsWith("#") || line.trim().isEmpty())
                    continue;

                String[] v = line.split(";", -1);
                if (v.length < 26)
                    continue;

                CaseDescription desc = new CaseDescription();
                desc.setId(Integer.parseInt(v[0].trim()));
                desc.setSud(v[1].trim());
                desc.setBrojPredmeta(v[2].trim());
                desc.setTipKrivicnogDjela(v[3].trim());
                desc.setClanKZ(v[4].trim());
                desc.setSaizvrsilastvo(v[5].trim());
                desc.setZabranjenoSredstvo(v[6].trim());
                desc.setLovostajIliZabranjeneVode(v[7].trim());
                desc.setVelikaKolicina(v[8].trim());
                desc.setElektricnaStruja(v[9].trim());
                desc.setAgregat(v[10].trim());
                desc.setSonda(v[11].trim());
                desc.setPretvarac(v[12].trim());
                desc.setPrisutanUlov(v[13].trim());
                desc.setKolicinaUlovaKg(parseDouble(v[14]));
                desc.setOduzimanjePredmeta(v[15].trim());
                desc.setRanijeOsudjivan(v[16].trim());
                desc.setKaznaZatvoraMjeseci(parseDouble(v[17]));
                desc.setKaznaZatvoraDani(parseInt(v[18]));
                desc.setRadUJavnomInteresuCasovi(parseInt(v[19]));
                desc.setRokProvjereGodine(parseInt(v[20]));
                desc.setTroskoviEur(parseDouble(v[21]));
                desc.setVrstaPresude(v[22].trim());
                desc.setUslovnaOsuda(v[23].trim());
                desc.setBrojOkrivljenih(parseInt(v[24]));
                desc.setBrojSvjedoka(parseInt(v[25]));

                CBRCase cbrCase = new CBRCase();
                cbrCase.setDescription(desc);
                cases.add(cbrCase);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return cases;
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public Collection<CBRCase> retrieveSomeCases(CaseBaseFilter filter) {
        return null;
    }

    @Override
    public void storeCases(Collection<CBRCase> cases) {
    }

    @Override
    public void close() {
    }

    @Override
    public void deleteCases(Collection<CBRCase> cases) {
    }

    @Override
    public void initFromXMLfile(URL url) throws InitializingException {
    }
}
