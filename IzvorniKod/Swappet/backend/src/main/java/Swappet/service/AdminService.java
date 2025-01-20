package Swappet.service;

import Swappet.controller.OglasDTO;
import Swappet.model.*;
import Swappet.repository.*;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

import java.io.ByteArrayOutputStream;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class AdminService {

    @Autowired
    private OglasRepository oglasRepository;

    @Autowired
    private TransakcijaRepository transakcijaRepository;

    @Autowired
    private UlaznicaRepository ulaznicaRepository;

    @Autowired
    private JeTipRepository jeTipRepository;

    @Autowired
    private SporRepository sporRepository;

    @Autowired
    private KorisnikRepository korisnikRepository;

    @Autowired
    private DeaktiviranOglasRepository deaktiviranOglasRepository;

    public List<OglasDTO> getAllOglasi() {
        List<Object[]> rawData = oglasRepository.findAllOglasi();
        List<OglasDTO> result = new ArrayList<>();

        for (Object[] row : rawData) {
            Oglas oglas = (Oglas) row[0];  // Oglas objekt
            Double price = (Double) row[1];  // cijena iz Ulaznice

            DeaktiviranOglas deaog = deaktiviranOglasRepository.findByIdoglas(oglas.getIdOglas());
            if (deaog != null && ChronoUnit.DAYS.between(deaog.getDvdeaktivacije(), LocalDate.now()) >= 14) {
                oglas.setAktivan(-2);
                oglasRepository.save(oglas);
            }

            OglasDTO dto = buildOglasDTO(oglas, price, null);
            result.add(dto);
        }

        return result;
    }

    public List<Transakcija> getAllTransactions() {
        return transakcijaRepository.findAll();
    }

    public byte[] generateReport() {
        List<Transakcija> transactions = transakcijaRepository.reportTransactions();
        int brojOglasa = oglasRepository.findRelevantOglasi().size();
        int brojUlaznica = transactions.size();
        double avgcijena = 0;

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(outputStream);

            com.itextpdf.kernel.pdf.PdfDocument pdf = new com.itextpdf.kernel.pdf.PdfDocument(writer);
            Document document = new Document(pdf);
            document.add(new Paragraph("Izvještaj generiran dana " + LocalDate.now() + ":\n"));

            for (Transakcija transakcija : transactions) {
                int id = transakcija.getIdTransakcija();
                String uspjesnost = "Greška";
                if (transakcija.getUspjesna() > 0) {
                    uspjesnost = "Uspješna";
                } else if (transakcija.getUspjesna() < 0) {
                    uspjesnost = "Neuspješna";
                } else if (transakcija.getUspjesna() == 0) {
                    uspjesnost = "Na čekanju";
                }
                String datum = transakcija.getDvPocetak().toString();
                int idulaznica = transakcija.getUlaznica().getIdUlaznica();

                double cijena = 0;
                if (transakcija.getUlaznica().getCijena() != null) {
                    cijena = transakcija.getUlaznica().getCijena();
                    avgcijena += cijena;
                }

                document.add(new Paragraph("Id transakcije: " + id));
                document.add(new Paragraph("Uspjeh: " + uspjesnost));
                document.add(new Paragraph("Datum: " + datum));
                document.add(new Paragraph("Prodana ulaznica: " + idulaznica));
                document.add(new Paragraph("Cijena ulaznice: " + cijena));
                document.add(new Paragraph("\n"));
            }

            avgcijena = avgcijena / brojUlaznica;
            document.add(new Paragraph("Broj aktivnih oglasa: " + brojOglasa));
            document.add(new Paragraph("Broj prodanih ulaznica: " + brojUlaznica));
            document.add(new Paragraph("Prosjecna cijena prodane ulaznice: " + String.format("%.2f", avgcijena) + "€"));

            document.close();
            byte[] pdfBytes = outputStream.toByteArray();

            return pdfBytes;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //aktivacija/deaktivacija oglasa
    public void activationRequest(Integer idOglas, Integer activationStatus) {
        Oglas oglas = oglasRepository.findByIdOglas(idOglas);

        oglas.setAktivan(activationStatus);
        oglasRepository.save(oglas);

        DeaktiviranOglas deaktiviranOglas = deaktiviranOglasRepository.findByIdoglas(idOglas);
        if (deaktiviranOglas == null && activationStatus == -1) {
            DeaktiviranOglas deaog = new DeaktiviranOglas(
                    idOglas,
                    LocalDate.now()
            );
            deaktiviranOglasRepository.save(deaog);
        } else if (deaktiviranOglas != null && activationStatus == -1) {
            deaktiviranOglas.setDvdeaktivacije(LocalDate.now());
            deaktiviranOglasRepository.save(deaktiviranOglas);
        }
    }

    //ban usera
    public void banUser(String email, Integer ban) {
        Korisnik korisnik = korisnikRepository.findByEmail(email);
        Spor spor = sporRepository.findByTuzen(korisnik);
        spor.setOdlukaSpor(ban);

        if (ban == 0) {
            korisnik.setKoristi(0);
            korisnikRepository.save(korisnik);
        }
    }

    //izvlači iz baze listu prijavljenih korisnika
    public List<String> reportedUsers() {
        List<Korisnik> reported = sporRepository.blame();
        List<String> reportedUsers = new ArrayList<>();
        for (Korisnik korisnik : reported) {
            reportedUsers.add(korisnik.getEmail());
        }
        return reportedUsers;
    }

    //izvlači iz baze sve admine
    public List<String> getAllAdmini() {
        List<Korisnik> admini = korisnikRepository.findAdmins();
        List<String> admins = new ArrayList<>();

        for (Korisnik korisnik : admini) {
            admins.add(korisnik.getEmail());
        }
        return admins;
    }

    // pomoć za konstrukciju OglasDTO
    private OglasDTO buildOglasDTO(Oglas oglas, Double price, Integer likedStatus) {
        String address = oglas.getUlica() + " " + oglas.getKucnibr() + ", " + oglas.getGrad();
        String date = oglas.getDatum().toString();
        Integer numberOfTickets = oglas.getAktivan();
        Integer ticketType = ulaznicaRepository.findALlUlaznica(oglas.getIdOglas()).getFirst().getVrstaUlaznice();
        Integer red = red = ulaznicaRepository.findALlUlaznica(oglas.getIdOglas()).getFirst().getRed();
        Integer broj = broj = ulaznicaRepository.findALlUlaznica(oglas.getIdOglas()).getFirst().getBroj();
        String tradeDescription = oglas.getOpisZamjene();
        Integer eventType = jeTipRepository.findByIdOglas(oglas.getIdOglas()).getIdDog();

        return new OglasDTO(
                oglas.getIdOglas(),
                oglas.getOpis(),
                oglas.getTipOglas().toString(),
                price,
                address,
                date,
                numberOfTickets,
                ticketType,
                broj,
                red,
                eventType,
                oglas.getKorisnik().getEmail(),
                tradeDescription,
                likedStatus
        );
    }
}