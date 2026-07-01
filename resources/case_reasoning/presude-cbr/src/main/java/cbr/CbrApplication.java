package cbr;

import java.util.Arrays;
import java.util.Collection;

import connector.CsvConnector;
import es.ucm.fdi.gaia.jcolibri.casebase.LinealCaseBase;
import es.ucm.fdi.gaia.jcolibri.cbraplications.StandardCBRApplication;
import es.ucm.fdi.gaia.jcolibri.cbrcore.Attribute;
import es.ucm.fdi.gaia.jcolibri.cbrcore.CBRCase;
import es.ucm.fdi.gaia.jcolibri.cbrcore.CBRCaseBase;
import es.ucm.fdi.gaia.jcolibri.cbrcore.CBRQuery;
import es.ucm.fdi.gaia.jcolibri.cbrcore.Connector;
import es.ucm.fdi.gaia.jcolibri.exception.ExecutionException;
import es.ucm.fdi.gaia.jcolibri.method.retrieve.RetrievalResult;
import es.ucm.fdi.gaia.jcolibri.method.retrieve.NNretrieval.NNConfig;
import es.ucm.fdi.gaia.jcolibri.method.retrieve.NNretrieval.NNScoringMethod;
import es.ucm.fdi.gaia.jcolibri.method.retrieve.NNretrieval.similarity.global.Average;
import es.ucm.fdi.gaia.jcolibri.method.retrieve.NNretrieval.similarity.local.Equal;
import es.ucm.fdi.gaia.jcolibri.method.retrieve.selection.SelectCases;
import model.CaseDescription;
import similarity.IntervalSimilarity;
import similarity.TabularSimilarity;
import similarity.ThresholdSimilarity;

/**
 * Aplikacija za rasudjivanje po slucajevima (CBR) za krivicna djela
 * protiv zivotne sredine (Glava 25 KZ CG).
 *
 * Koristimo jCOLIBRI okvir sa KNN pretragom i funkcijama slicnosti
 * modelovanim za kljucne cinjenice iz oblasti nezakonitog lova i ribolova:
 *
 *  1. tipKrivicnogDjela          - TabularSimilarity (nezakonit ribolov vs nezakonit lov)
 *  2. clanKZ                     - TabularSimilarity (cl. 325 st.1-5, cl. 326 st.1-4)
 *  3. saizvrsilastvo             - Equal (da/ne)
 *  4. zabranjenoSredstvo         - Equal (da/ne)
 *  5. lovostajIliZabranjeneVode  - Equal (da/ne)
 *  6. velikaKolicina             - Equal (da/ne)
 *  7. elektricnaStruja           - Equal (da/ne)
 *  8. agregat                    - Equal (da/ne)
 *  9. sonda                      - Equal (da/ne)
 * 10. pretvarac                  - Equal (da/ne)
 * 11. prisutanUlov               - Equal (da/ne)
 * 12. kolicinaUlovaKg            - IntervalSimilarity (interval 50 kg)
 * 13. oduzimanjePredmeta         - Equal (da/ne)
 * 14. ranijeOsudjivan            - TabularSimilarity (da/ne/nepoznat)
 * 15. kaznaZatvoraMjeseci        - IntervalSimilarity (interval 24 mjeseci)
 * 16. kaznaZatvoraDani           - IntervalSimilarity (interval 365 dana)
 * 17. vrstaPresude               - TabularSimilarity (osudjujuca/oslobadjajuca/uslovna)
 * 18. uslovnaOsuda               - Equal
 * 19. brojOkrivljenih            - ThresholdSimilarity (prag 2)
 * 20. brojSvjedoka               - IntervalSimilarity (interval 10)
 *
 * Globalna slicnost: prosjek svih lokalnih slicnosti (Average).
 */
public class CbrApplication implements StandardCBRApplication {

    Connector _connector;
    CBRCaseBase _caseBase;
    NNConfig simConfig;

    @Override
    public void configure() throws ExecutionException {
        _connector = new CsvConnector();
        _caseBase = new LinealCaseBase();
        simConfig = new NNConfig();

        // Globalna funkcija slicnosti = prosjek
        simConfig.setDescriptionSimFunction(new Average());

        // ===== 1. Tip krivicnog djela =====
        // Obrazlozenje: Nezakonit lov i nezakonit ribolov su razlicita krivicna
        // djela ali oba spadaju u istu glavu (25) KZ CG - krivicna djela protiv
        // zivotne sredine. Slicnost je 0.4 jer dijele zasticenu vrijednost
        // (zivotna sredina) ali imaju razlicite elemente bica djela.
        TabularSimilarity slicnostTipa = new TabularSimilarity(Arrays.asList(
                "nezakonit ribolov",
                "nezakonit lov"
        ));
        slicnostTipa.setSimilarity("nezakonit ribolov", "nezakonit lov", 0.4);
        simConfig.addMapping(new Attribute("tipKrivicnogDjela", CaseDescription.class), slicnostTipa);

        // ===== 2. Clan Krivicnog zakonika =====
        // Obrazlozenje: Clanovi istog krivicnog djela (npr. razliciti stavovi
        // clana 326) su medjusobno slicniji nego clanovi razlicitih djela.
        // cl. 326 st. 1 vs cl. 326 st. 2 = 0.7 (oba ribolov, razliciti osnovi)
        // cl. 325 st. 1 vs cl. 325 st. 2 = 0.7
        // cl. 325 vs cl. 326 (razlicita djela) = 0.3
        TabularSimilarity slicnostClana = new TabularSimilarity(Arrays.asList(
                "325.1", "325.2", "325.3", "325.4", "325.5",
                "326.1", "326.2", "326.3", "326.4"
        ));
        // Unutar clana 325 (nezakonit lov)
        slicnostClana.setSimilarity("325.1", "325.2", 0.7);
        slicnostClana.setSimilarity("325.1", "325.3", 0.5);
        slicnostClana.setSimilarity("325.1", "325.4", 0.5);
        slicnostClana.setSimilarity("325.1", "325.5", 0.4);
        slicnostClana.setSimilarity("325.2", "325.3", 0.6);
        slicnostClana.setSimilarity("325.2", "325.4", 0.5);
        slicnostClana.setSimilarity("325.2", "325.5", 0.4);
        slicnostClana.setSimilarity("325.3", "325.4", 0.6);
        slicnostClana.setSimilarity("325.3", "325.5", 0.5);
        slicnostClana.setSimilarity("325.4", "325.5", 0.5);
        // Unutar clana 326 (nezakonit ribolov)
        slicnostClana.setSimilarity("326.1", "326.2", 0.7);
        slicnostClana.setSimilarity("326.1", "326.3", 0.5);
        slicnostClana.setSimilarity("326.1", "326.4", 0.4);
        slicnostClana.setSimilarity("326.2", "326.3", 0.6);
        slicnostClana.setSimilarity("326.2", "326.4", 0.5);
        slicnostClana.setSimilarity("326.3", "326.4", 0.5);
        // Izmedju clanova 325 i 326 (razlicita krivicna djela iste glave)
        slicnostClana.setSimilarity("325.1", "326.1", 0.3);
        slicnostClana.setSimilarity("325.1", "326.2", 0.3);
        slicnostClana.setSimilarity("325.2", "326.1", 0.3);
        slicnostClana.setSimilarity("325.2", "326.2", 0.3);
        slicnostClana.setSimilarity("325.3", "326.3", 0.3);
        slicnostClana.setSimilarity("325.4", "326.2", 0.3);
        simConfig.addMapping(new Attribute("clanKZ", CaseDescription.class), slicnostClana);

        // ===== 3. Saizvrsilastvo =====
        // Obrazlozenje: Zajednicko izvrsenje djela (cl. 23 st. 2) je
        // otezavajuca okolnost. Binarno: da/ne.
        simConfig.addMapping(new Attribute("saizvrsilastvo", CaseDescription.class), new Equal());

        // ===== 4. Zabranjeno sredstvo =====
        // Obrazlozenje: Koriscenje zabranjenog sredstva je kvalifikatorna okolnost.
        simConfig.addMapping(new Attribute("zabranjenoSredstvo", CaseDescription.class), new Equal());

        // ===== 5. Lovostaj ili zabranjene vode =====
        simConfig.addMapping(new Attribute("lovostajIliZabranjeneVode", CaseDescription.class), new Equal());

        // ===== 6. Velika kolicina =====
        simConfig.addMapping(new Attribute("velikaKolicina", CaseDescription.class), new Equal());

        // ===== 7. Elektricna struja =====
        simConfig.addMapping(new Attribute("elektricnaStruja", CaseDescription.class), new Equal());

        // ===== 8. Elektroagregat =====
        simConfig.addMapping(new Attribute("agregat", CaseDescription.class), new Equal());

        // ===== 9. Sonda =====
        simConfig.addMapping(new Attribute("sonda", CaseDescription.class), new Equal());

        // ===== 10. Pretvarac =====
        simConfig.addMapping(new Attribute("pretvarac", CaseDescription.class), new Equal());

        // ===== 11. Prisutan ulov =====
        simConfig.addMapping(new Attribute("prisutanUlov", CaseDescription.class), new Equal());

        // ===== 12. Kolicina ulova (kg) =====
        // Obrazlozenje: Kolicina ulova utice na kvalifikaciju i odmjeravanje kazne.
        // Interval 50 kg pokriva raspon uocen u nasim slucajevima (0-30+ kg).
        simConfig.addMapping(new Attribute("kolicinaUlovaKg", CaseDescription.class),
                new IntervalSimilarity(50));

        // ===== 13. Oduzimanje predmeta =====
        simConfig.addMapping(new Attribute("oduzimanjePredmeta", CaseDescription.class), new Equal());

        // ===== 14. Ranije osudjivan =====
        // Obrazlozenje: Recidivizam je bitan faktor pri odlucivanju o kazni.
        // "da" vs "ne" = 0.0 (potpuno razlicito jer recidivist dobija vecu kaznu)
        // "nepoznat" vs "da"/"ne" = 0.3 (nedostatak informacije)
        TabularSimilarity slicnostRecidivizma = new TabularSimilarity(Arrays.asList(
                "da", "ne", "nepoznat"
        ));
        slicnostRecidivizma.setSimilarity("da", "ne", 0.0);
        slicnostRecidivizma.setSimilarity("da", "nepoznat", 0.3);
        slicnostRecidivizma.setSimilarity("ne", "nepoznat", 0.3);
        simConfig.addMapping(new Attribute("ranijeOsudjivan", CaseDescription.class), slicnostRecidivizma);

        // ===== 15. Kazna zatvora u mjesecima =====
        // Obrazlozenje: Trajanje zatvorske kazne. Interval 24 mjeseci
        // jer je maksimum za cl. 326 st. 3 tri godine (36 mj), a vecina
        // kazni u nasim slucajevima je ispod 12 mjeseci.
        simConfig.addMapping(new Attribute("kaznaZatvoraMjeseci", CaseDescription.class),
                new IntervalSimilarity(24));

        // ===== 16. Kazna zatvora u danima =====
        // Obrazlozenje: Mnoge kazne za ekoloska krivicna djela se izricu u
        // danima (30, 40, 60, 90 dana). Interval 365 dana (jedna godina).
        simConfig.addMapping(new Attribute("kaznaZatvoraDani", CaseDescription.class),
                new IntervalSimilarity(365));

        // ===== 17. Vrsta presude =====
        // Obrazlozenje: Osudjujuca i uslovna su slicnije (obe znace krivicu)
        // nego oslobadjajuca. Uslovna i osudjujuca = 0.6.
        TabularSimilarity slicnostPresude = new TabularSimilarity(Arrays.asList(
                "osudjujuca", "uslovna", "oslobadjajuca", "nepoznat"
        ));
        slicnostPresude.setSimilarity("osudjujuca", "uslovna", 0.6);
        slicnostPresude.setSimilarity("osudjujuca", "oslobadjajuca", 0.1);
        slicnostPresude.setSimilarity("uslovna", "oslobadjajuca", 0.1);
        slicnostPresude.setSimilarity("osudjujuca", "nepoznat", 0.2);
        slicnostPresude.setSimilarity("uslovna", "nepoznat", 0.2);
        slicnostPresude.setSimilarity("oslobadjajuca", "nepoznat", 0.2);
        simConfig.addMapping(new Attribute("vrstaPresude", CaseDescription.class), slicnostPresude);

        // ===== 18. Uslovna osuda =====
        simConfig.addMapping(new Attribute("uslovnaOsuda", CaseDescription.class), new Equal());

        // ===== 19. Broj okrivljenih =====
        // Obrazlozenje: Organizovano izvrsenje (vise lica) je otezavajuca okolnost.
        simConfig.addMapping(new Attribute("brojOkrivljenih", CaseDescription.class),
                new ThresholdSimilarity(2));

        // ===== 20. Broj svjedoka =====
        simConfig.addMapping(new Attribute("brojSvjedoka", CaseDescription.class),
                new IntervalSimilarity(10));
    }

    @Override
    public CBRCaseBase preCycle() throws ExecutionException {
        _caseBase.init(_connector);
        Collection<CBRCase> cases = _caseBase.getCases();
        System.out.println("=== Ucitano " + cases.size() + " slucajeva iz baze ===\n");
        return _caseBase;
    }

    @Override
    public void cycle(CBRQuery query) throws ExecutionException {
        // KNN pretraga - pronalazi najslicnije slucajeve
        Collection<RetrievalResult> eval =
                NNScoringMethod.evaluateSimilarity(_caseBase.getCases(), query, simConfig);

        // Izbor top 5 najslicnijih
        eval = SelectCases.selectTopKRR(eval, 5);

        System.out.println("=== Pronadjeni slicni slucajevi (top 5) ===\n");
        int rank = 1;
        for (RetrievalResult rr : eval) {
            CaseDescription desc = (CaseDescription) rr.get_case().getDescription();
            System.out.printf("  %d. Slicnost: %.2f%%%n", rank, rr.getEval() * 100);
            System.out.printf("     %s%n", desc);
            System.out.printf("     Clan: %s | Presuda: %s | Kazna: %.0f mj / %d dana%n%n",
                    desc.getClanKZ(), desc.getVrstaPresude(),
                    desc.getKaznaZatvoraMjeseci(), desc.getKaznaZatvoraDani());
            rank++;
        }
    }

    @Override
    public void postCycle() throws ExecutionException {
        // Ovdje se novi slucaj moze sacuvati u bazu (CSV)
    }

    /**
     * Primjer pokretanja CBR sistema sa novim slucajem.
     */
    public static void main(String[] args) {
        StandardCBRApplication cbr = new CbrApplication();
        try {
            cbr.configure();
            cbr.preCycle();

            // ===== Novi slucaj za rasudjivanje =====
            CBRQuery query = new CBRQuery();
            CaseDescription noviSlucaj = new CaseDescription();

            // Primer 1: Nezakonit ribolov - okrivljeni koristio elektricnu struju
            // i elektroagregat za ribolov na Skadarskom jezeru, zatecen sa 8.5 kg ribe
            noviSlucaj.setTipKrivicnogDjela("nezakonit ribolov");
            noviSlucaj.setClanKZ("326.2");
            noviSlucaj.setSaizvrsilastvo("da");
            noviSlucaj.setZabranjenoSredstvo("da");
            noviSlucaj.setLovostajIliZabranjeneVode("ne");
            noviSlucaj.setVelikaKolicina("ne");
            noviSlucaj.setElektricnaStruja("da");
            noviSlucaj.setAgregat("da");
            noviSlucaj.setSonda("da");
            noviSlucaj.setPretvarac("da");
            noviSlucaj.setPrisutanUlov("da");
            noviSlucaj.setKolicinaUlovaKg(8.5);
            noviSlucaj.setOduzimanjePredmeta("da");
            noviSlucaj.setRanijeOsudjivan("ne");
            noviSlucaj.setKaznaZatvoraMjeseci(0);      // nepoznato - trazimo predlog
            noviSlucaj.setKaznaZatvoraDani(0);
            noviSlucaj.setVrstaPresude("uslovna");
            noviSlucaj.setUslovnaOsuda("Da");
            noviSlucaj.setBrojOkrivljenih(2);
            noviSlucaj.setBrojSvjedoka(2);

            query.setDescription(noviSlucaj);

            System.out.println("=== Novi slucaj za rasudjivanje ===");
            System.out.println(noviSlucaj);
            System.out.println();

            cbr.cycle(query);

            // Primer 2: Nezakonit ribolov - pojedinac, ranije osudjivan,
            // manji ulov, bez saizvrsilaca
            System.out.println("\n========================================\n");

            CBRQuery query2 = new CBRQuery();
            CaseDescription noviSlucaj2 = new CaseDescription();
            noviSlucaj2.setTipKrivicnogDjela("nezakonit ribolov");
            noviSlucaj2.setClanKZ("326.2");
            noviSlucaj2.setSaizvrsilastvo("ne");
            noviSlucaj2.setZabranjenoSredstvo("da");
            noviSlucaj2.setLovostajIliZabranjeneVode("ne");
            noviSlucaj2.setVelikaKolicina("ne");
            noviSlucaj2.setElektricnaStruja("da");
            noviSlucaj2.setAgregat("da");
            noviSlucaj2.setSonda("ne");
            noviSlucaj2.setPretvarac("ne");
            noviSlucaj2.setPrisutanUlov("da");
            noviSlucaj2.setKolicinaUlovaKg(3.0);
            noviSlucaj2.setOduzimanjePredmeta("da");
            noviSlucaj2.setRanijeOsudjivan("da");
            noviSlucaj2.setKaznaZatvoraMjeseci(0);
            noviSlucaj2.setKaznaZatvoraDani(0);
            noviSlucaj2.setVrstaPresude("osudjujuca");
            noviSlucaj2.setUslovnaOsuda("Ne");
            noviSlucaj2.setBrojOkrivljenih(1);
            noviSlucaj2.setBrojSvjedoka(3);

            query2.setDescription(noviSlucaj2);

            System.out.println("=== Novi slucaj za rasudjivanje ===");
            System.out.println(noviSlucaj2);
            System.out.println();

            cbr.cycle(query2);

            cbr.postCycle();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
