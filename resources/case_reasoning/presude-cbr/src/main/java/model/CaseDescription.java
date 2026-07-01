package model;

import es.ucm.fdi.gaia.jcolibri.cbrcore.Attribute;
import es.ucm.fdi.gaia.jcolibri.cbrcore.CaseComponent;

/**
 * Opisuje kljucne cinjenice jednog slucaja iz oblasti krivicnih djela
 * protiv zivotne sredine (Glava 25 KZ CG).
 *
 * Atributi:
 *  1. tipKrivicnogDjela       - vrsta krivicnog djela (nezakonit ribolov / nezakonit lov)
 *  2. clanKZ                  - clan Krivicnog zakonika (325.1-325.5, 326.1-326.4)
 *  3. saizvrsilastvo          - da li postoji saizvrsilastvo (cl. 23 st. 2)
 *  4. zabranjenoSredstvo      - da li je korisceno zabranjeno sredstvo
 *  5. lovostajIliZabranjeneVode - da li je djelo u lovostaju / zabranjenim vodama
 *  6. velikaKolicina          - da li je ulovljena velika kolicina
 *  7. elektricnaStruja        - da li je koriscena elektricna struja
 *  8. agregat                 - da li je koriscen elektroagregat
 *  9. sonda                   - da li je koriscena sonda
 * 10. pretvarac               - da li je koriscen pretvarac
 * 11. prisutanUlov            - da li je zatecen ulov
 * 12. kolicinaUlovaKg         - kolicina ulova u kg
 * 13. oduzimanjePredmeta      - da li su predmeti oduzeti
 * 14. ranijeOsudjivan         - da li je okrivljeni ranije osudjivan
 * 15. kaznaZatvoraMjeseci     - izrecena kazna u mjesecima
 * 16. kaznaZatvoraDani        - izrecena kazna u danima
 * 17. radUJavnomInteresuCasovi - rad u javnom interesu (casovi)
 * 18. rokProvjereGodine       - rok provjere uslovne osude (godine)
 * 19. troskoviEur             - troskovi postupka (EUR)
 * 20. vrstaPresude            - tip presude (osudjujuca/oslobadjajuca/uslovna)
 * 21. uslovnaOsuda            - da li je izrecena uslovna osuda
 * 22. brojOkrivljenih         - broj okrivljenih lica
 * 23. brojSvjedoka            - broj svjedoka
 */
public class CaseDescription implements CaseComponent {

    private int id;
    private String sud;
    private String brojPredmeta;
    private String tipKrivicnogDjela;
    private String clanKZ;
    private String saizvrsilastvo;
    private String zabranjenoSredstvo;
    private String lovostajIliZabranjeneVode;
    private String velikaKolicina;
    private String elektricnaStruja;
    private String agregat;
    private String sonda;
    private String pretvarac;
    private String prisutanUlov;
    private double kolicinaUlovaKg;
    private String oduzimanjePredmeta;
    private String ranijeOsudjivan;
    private double kaznaZatvoraMjeseci;
    private int kaznaZatvoraDani;
    private int radUJavnomInteresuCasovi;
    private int rokProvjereGodine;
    private double troskoviEur;
    private String vrstaPresude;
    private String uslovnaOsuda;
    private int brojOkrivljenih;
    private int brojSvjedoka;

    // ---- Getteri i setteri ----

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getSud() { return sud; }
    public void setSud(String sud) { this.sud = sud; }

    public String getBrojPredmeta() { return brojPredmeta; }
    public void setBrojPredmeta(String brojPredmeta) { this.brojPredmeta = brojPredmeta; }

    public String getTipKrivicnogDjela() { return tipKrivicnogDjela; }
    public void setTipKrivicnogDjela(String tipKrivicnogDjela) { this.tipKrivicnogDjela = tipKrivicnogDjela; }

    public String getClanKZ() { return clanKZ; }
    public void setClanKZ(String clanKZ) { this.clanKZ = clanKZ; }

    public String getSaizvrsilastvo() { return saizvrsilastvo; }
    public void setSaizvrsilastvo(String saizvrsilastvo) { this.saizvrsilastvo = saizvrsilastvo; }

    public String getZabranjenoSredstvo() { return zabranjenoSredstvo; }
    public void setZabranjenoSredstvo(String zabranjenoSredstvo) { this.zabranjenoSredstvo = zabranjenoSredstvo; }

    public String getLovostajIliZabranjeneVode() { return lovostajIliZabranjeneVode; }
    public void setLovostajIliZabranjeneVode(String lovostajIliZabranjeneVode) { this.lovostajIliZabranjeneVode = lovostajIliZabranjeneVode; }

    public String getVelikaKolicina() { return velikaKolicina; }
    public void setVelikaKolicina(String velikaKolicina) { this.velikaKolicina = velikaKolicina; }

    public String getElektricnaStruja() { return elektricnaStruja; }
    public void setElektricnaStruja(String elektricnaStruja) { this.elektricnaStruja = elektricnaStruja; }

    public String getAgregat() { return agregat; }
    public void setAgregat(String agregat) { this.agregat = agregat; }

    public String getSonda() { return sonda; }
    public void setSonda(String sonda) { this.sonda = sonda; }

    public String getPretvarac() { return pretvarac; }
    public void setPretvarac(String pretvarac) { this.pretvarac = pretvarac; }

    public String getPrisutanUlov() { return prisutanUlov; }
    public void setPrisutanUlov(String prisutanUlov) { this.prisutanUlov = prisutanUlov; }

    public double getKolicinaUlovaKg() { return kolicinaUlovaKg; }
    public void setKolicinaUlovaKg(double kolicinaUlovaKg) { this.kolicinaUlovaKg = kolicinaUlovaKg; }

    public String getOduzimanjePredmeta() { return oduzimanjePredmeta; }
    public void setOduzimanjePredmeta(String oduzimanjePredmeta) { this.oduzimanjePredmeta = oduzimanjePredmeta; }

    public String getRanijeOsudjivan() { return ranijeOsudjivan; }
    public void setRanijeOsudjivan(String ranijeOsudjivan) { this.ranijeOsudjivan = ranijeOsudjivan; }

    public double getKaznaZatvoraMjeseci() { return kaznaZatvoraMjeseci; }
    public void setKaznaZatvoraMjeseci(double kaznaZatvoraMjeseci) { this.kaznaZatvoraMjeseci = kaznaZatvoraMjeseci; }

    public int getKaznaZatvoraDani() { return kaznaZatvoraDani; }
    public void setKaznaZatvoraDani(int kaznaZatvoraDani) { this.kaznaZatvoraDani = kaznaZatvoraDani; }

    public int getRadUJavnomInteresuCasovi() { return radUJavnomInteresuCasovi; }
    public void setRadUJavnomInteresuCasovi(int radUJavnomInteresuCasovi) { this.radUJavnomInteresuCasovi = radUJavnomInteresuCasovi; }

    public int getRokProvjereGodine() { return rokProvjereGodine; }
    public void setRokProvjereGodine(int rokProvjereGodine) { this.rokProvjereGodine = rokProvjereGodine; }

    public double getTroskoviEur() { return troskoviEur; }
    public void setTroskoviEur(double troskoviEur) { this.troskoviEur = troskoviEur; }

    public String getVrstaPresude() { return vrstaPresude; }
    public void setVrstaPresude(String vrstaPresude) { this.vrstaPresude = vrstaPresude; }

    public String getUslovnaOsuda() { return uslovnaOsuda; }
    public void setUslovnaOsuda(String uslovnaOsuda) { this.uslovnaOsuda = uslovnaOsuda; }

    public int getBrojOkrivljenih() { return brojOkrivljenih; }
    public void setBrojOkrivljenih(int brojOkrivljenih) { this.brojOkrivljenih = brojOkrivljenih; }

    public int getBrojSvjedoka() { return brojSvjedoka; }
    public void setBrojSvjedoka(int brojSvjedoka) { this.brojSvjedoka = brojSvjedoka; }

    @Override
    public String toString() {
        return "Slucaj [id=" + id
                + ", sud=" + sud
                + ", br=" + brojPredmeta
                + ", tip=" + tipKrivicnogDjela
                + ", clan=" + clanKZ
                + ", saizvrsilastvo=" + saizvrsilastvo
                + ", zabranjenoSredstvo=" + zabranjenoSredstvo
                + ", lovostajIliZabranjeneVode=" + lovostajIliZabranjeneVode
                + ", velikaKolicina=" + velikaKolicina
                + ", elektricnaStruja=" + elektricnaStruja
                + ", agregat=" + agregat
                + ", sonda=" + sonda
                + ", pretvarac=" + pretvarac
                + ", prisutanUlov=" + prisutanUlov
                + ", kolicinaUlovaKg=" + kolicinaUlovaKg + "kg"
                + ", oduzimanjePredmeta=" + oduzimanjePredmeta
                + ", ranijeOsudj=" + ranijeOsudjivan
                + ", kaznaMj=" + kaznaZatvoraMjeseci
                + ", kaznaDani=" + kaznaZatvoraDani
                + ", radJI=" + radUJavnomInteresuCasovi + "h"
                + ", presuda=" + vrstaPresude
                + ", uslovna=" + uslovnaOsuda
                + ", brojOkrivljenih=" + brojOkrivljenih
                + ", brojSvjedoka=" + brojSvjedoka
                + "]";
    }

    @Override
    public Attribute getIdAttribute() {
        return new Attribute("id", this.getClass());
    }
}
