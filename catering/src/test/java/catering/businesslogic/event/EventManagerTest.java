package catering.businesslogic.event;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Date;
import java.sql.Time;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;

/**
 * Comprehensive JUnit 5 test suite for the "Gestire gli eventi" use case ({@link EventManager}).
 * Organized with {@link Nested} classes per DSD operation, covering both happy paths
 * and precondition/ALT-clause violations via {@code assertThrows}.
 */
@DisplayName("EventManager - UC Gestire gli eventi")
public class EventManagerTest {

    private static CatERing app;
    private static EventManager em;
    private static User organizer;
    private static User chef;
    private static User cook;
    private static User serviceStaff;

    @BeforeAll
    static void init() {
        PersistenceManager.initializeDatabase("database/catering_init_sqlite.sql");
        app = CatERing.getInstance();
        em = app.getEventManager();
        organizer = User.load("Carlin");    // Role: ORGANIZER
        chef = User.load("Antonio");        // Role: CHEF, CUOCO
        cook = User.load("Luca");           // Role: CUOCO
        serviceStaff = User.load("Marco");  // Role: SERVIZIO
    }

    @BeforeEach
    void setup() throws UseCaseLogicException {
        app.getUserManager().fakeLogin(organizer.getUserName());
        em.clearSelection();
    }

    @Nested
    @DisplayName("1. Creazione Evento (createEvent)")
    class CreateEventTests {

        @Test
        @DisplayName("Happy path: Organizzatore crea evento e verifica persistenza")
        void testCreateEvent_AsOrganizer_PersistsCorrectly() throws UseCaseLogicException {
            Date start = Date.valueOf("2026-06-10");
            Date end = Date.valueOf("2026-06-11");

            Event ev = em.createEvent("Matrimonio Reale", "Famiglia Rossi", "Castello di Rivoli",
                    start, end, 120, "Elegante");

            assertNotNull(ev);
            assertTrue(ev.getId() > 0);
            assertEquals(Event.State.IN_LAVORAZIONE, ev.getEventState());
            assertEquals("in lavorazione", ev.getState());
            assertEquals(organizer, ev.getOrganizer());

            Event loaded = Event.loadById(ev.getId());
            assertNotNull(loaded);
            assertEquals("Matrimonio Reale", loaded.getName());
            assertEquals(120, loaded.getExpectedParticipants());
        }

        @Test
        @DisplayName("ALT: Utente non Organizzatore (Chef o Cuoco) riceve UseCaseLogicException")
        void testCreateEvent_NonOrganizer_ThrowsException() throws UseCaseLogicException {
            app.getUserManager().fakeLogin(chef.getUserName());
            assertThrows(UseCaseLogicException.class, () ->
                    em.createEvent("Evento Non Autorizzato", "Torino",
                            Date.valueOf("2026-06-10"), Date.valueOf("2026-06-10")));

            app.getUserManager().fakeLogin(cook.getUserName());
            assertThrows(UseCaseLogicException.class, () ->
                    em.createEvent("Evento Cuoco", "Torino",
                            Date.valueOf("2026-06-10"), Date.valueOf("2026-06-10")));
        }

        @Test
        @DisplayName("ALT: Nome vuoto, data fine < data inizio o partecipanti negativi")
        void testCreateEvent_InvalidParameters_ThrowsException() {
            Date d1 = Date.valueOf("2026-06-15");
            Date d2 = Date.valueOf("2026-06-10");

            assertThrows(UseCaseLogicException.class, () -> em.createEvent("", "Torino", d2, d1));
            assertThrows(UseCaseLogicException.class, () -> em.createEvent("Date Invertite", "Torino", d1, d2));
            assertThrows(UseCaseLogicException.class, () -> em.createEvent("Negativi", "Cli", "Torino", d2, d1, -5, null));
        }
    }

    @Nested
    @DisplayName("1a. Selezione e Modifica Evento (selectEvent / modifyEvent)")
    class SelectAndModifyEventTests {

        @Test
        @DisplayName("selectEvent valido e null")
        void testSelectEvent() throws UseCaseLogicException {
            Event ev = em.createEvent("Evento Selezionabile", "Torino",
                    Date.valueOf("2026-07-01"), Date.valueOf("2026-07-01"));
            em.clearSelection();

            Event selected = em.selectEvent(ev);
            assertEquals(ev, selected);
            assertEquals(ev, em.getCurrentEvent());

            assertThrows(UseCaseLogicException.class, () -> em.selectEvent(null));
        }

        @Test
        @DisplayName("modifyEvent in stato 'in corso': vincolo ±30% partecipanti e blocco cambio date")
        void testModifyEvent_InCorsoConstraints() throws UseCaseLogicException {
            Date start = Date.valueOf("2026-08-10");
            Date end = Date.valueOf("2026-08-10");
            Event ev = em.createEvent("Gala Estate", "VIP", "Villa d'Este", start, end, 100, "Formale");
            Service srv = em.createService("Cena", start, Time.valueOf("20:00:00"), Time.valueOf("23:00:00"), "Salone", 100);
            em.approveMenu(srv, Menu.load(86), null);

            assertEquals(Event.State.IN_CORSO, ev.getEventState());

            // Modifica partecipanti entro ±30% (100 -> 125) consentita
            em.modifyEvent(null, "Nuova Ala Villa", null, null, 125, "Note aggiornate");
            assertEquals(125, ev.getExpectedParticipants());
            assertEquals("Nuova Ala Villa", ev.getLocation());

            // Modifica partecipanti oltre ±30% (da 125 -> 200) vietata
            assertThrows(UseCaseLogicException.class, () ->
                    em.modifyEvent(null, null, null, null, 200, null));

            // Modifica date o aggiunta giorni vietata in stato "in corso"
            assertThrows(UseCaseLogicException.class, () ->
                    em.modifyEvent(null, null, Date.valueOf("2026-08-11"), Date.valueOf("2026-08-12"), 0, null));
            assertThrows(UseCaseLogicException.class, () ->
                    em.modifyEvent(null, null, start, Date.valueOf("2026-08-12"), 0, null));
        }

        @Test
        @DisplayName("ALT: modifyEvent su evento terminato o annullato lancia eccezione")
        void testModifyEvent_ClosedEvent_ThrowsException() throws UseCaseLogicException {
            em.createEvent("Evento Chiuso", "Torino", Date.valueOf("2026-08-01"), Date.valueOf("2026-08-01"));
            em.cancelEvent();

            assertThrows(UseCaseLogicException.class, () ->
                    em.modifyEvent("Nuovo Nome", null, null, null, 0, null));
        }
    }

    @Nested
    @DisplayName("3. Assegnazione Chef (assignChef)")
    class AssignChefTests {

        @Test
        @DisplayName("Happy path e ALT utente non Chef / nessun evento attivo")
        void testAssignChef() throws UseCaseLogicException {
            assertThrows(UseCaseLogicException.class, () -> em.assignChef(chef));

            Event ev = em.createEvent("Pranzo Aziendale", "Torino",
                    Date.valueOf("2026-09-01"), Date.valueOf("2026-09-01"));

            em.assignChef(chef);
            assertEquals(chef.getId(), ev.getChefId());

            assertThrows(UseCaseLogicException.class, () -> em.assignChef(cook));
            assertThrows(UseCaseLogicException.class, () -> em.assignChef(null));
        }
    }

    @Nested
    @DisplayName("4. Gestione Servizi (createService, selectService, modifyServiceParticipants, deleteOrCancelService)")
    class ServiceManagementTests {

        @Test
        @DisplayName("selectService verifica appartenenza all'evento corrente")
        void testSelectService() throws UseCaseLogicException {
            Date day = Date.valueOf("2026-09-15");
            em.createEvent("Convegno Autunno", "Lingotto", day, day);
            Service s1 = em.createService("Coffee Break", day, Time.valueOf("10:00:00"), Time.valueOf("11:00:00"), "Sala A");
            Service s2 = em.createService("Pranzo", day, Time.valueOf("13:00:00"), Time.valueOf("15:00:00"), "Sala B");

            assertEquals(s2, em.getCurrentService());
            assertEquals(s1, em.selectService(s1));
            assertEquals(s1, em.getCurrentService());

            Service orphan = new Service("Servizio Esterno");
            assertThrows(UseCaseLogicException.class, () -> em.selectService(orphan));
        }

        @Test
        @DisplayName("deleteOrCancelService: rimuove in 'in lavorazione', annulla in 'in corso'")
        void testDeleteOrCancelService() throws UseCaseLogicException {
            Date day = Date.valueOf("2026-09-20");
            Event ev = em.createEvent("Fiera Design", "Piazza Castello", day, day);
            Service s1 = em.createService("Aperitivo", day, Time.valueOf("18:00:00"), Time.valueOf("20:00:00"), "Terrazza", 80);
            Service s2 = em.createService("Cena", day, Time.valueOf("20:30:00"), Time.valueOf("23:00:00"), "Salone", 80);

            // In lavorazione: elimina fisicamente il servizio
            em.deleteOrCancelService(s1);
            assertFalse(ev.containsService(s1));
            assertNull(Service.loadById(s1.getId()));

            // Approvazione menù -> evento "in corso" -> deleteOrCancelService imposta stato ad "annullato"
            em.approveMenu(s2, Menu.load(86), "Senza lattosio");
            assertEquals(Event.State.IN_CORSO, ev.getEventState());

            em.deleteOrCancelService(s2);
            assertTrue(ev.containsService(s2));
            assertEquals(Service.State.ANNULLATO, s2.getServiceState());
            assertEquals("annullato", Service.loadById(s2.getId()).getState());
        }

        @Test
        @DisplayName("ALT: aggiunta servizio bloccata se evento 'in corso' e vincolo ±30% sui partecipanti")
        void testInCorsoServiceConstraints() throws UseCaseLogicException {
            Date day = Date.valueOf("2026-09-25");
            Event ev = em.createEvent("Gala", "Cliente", "Torino", day, day, 100, null);
            Service srv = em.createService("Cena Placée", day, Time.valueOf("20:00:00"), Time.valueOf("23:30:00"), "Sala 1", 100);
            em.approveMenu(srv, Menu.load(86), "Aggiungere opzione vegana");

            assertThrows(UseCaseLogicException.class, () -> em.addService("Dopo Cena"));

            em.modifyServiceParticipants(srv, 120);
            assertEquals(120, srv.getExpectedParticipants());

            assertThrows(UseCaseLogicException.class, () -> em.modifyServiceParticipants(srv, 190));
            assertThrows(UseCaseLogicException.class, () -> em.modifyServiceParticipants(srv, 0));
        }
    }

    @Nested
    @DisplayName("6-7. Turni e Personale (addShiftAndRequestStaff, assignStaffMember, removeStaffRequest)")
    class StaffRequestTests {

        @Test
        @DisplayName("Happy path: richiesta personale, assegnazione SERVIZIO/CUOCO e rimozione con liberazione turno")
        void testStaffRequestFullFlow() throws UseCaseLogicException {
            Date day = Date.valueOf("2026-10-01");
            em.createEvent("Banchetto", "Villa Reale", day, day);
            Service srv = em.createService("Pranzo", day, Time.valueOf("12:00:00"), Time.valueOf("15:00:00"), "Salone");

            StaffRequest req = em.addShiftAndRequestStaff(day, Time.valueOf("11:00:00"), Time.valueOf("16:00:00"), "Cameriere", 4);
            assertNotNull(req);
            assertTrue(req.getId() > 0);
            assertEquals(1, srv.getStaffRequests().size());

            // Assegnazione membro del personale di servizio
            em.assignStaffMember(req, serviceStaff, "Servizio bevande");
            assertEquals(serviceStaff, req.getAssignedUser());
            assertEquals("Servizio bevande", req.getRole());
            assertTrue(req.getShift().isBooked(serviceStaff));

            // Assegnazione cuoco di supporto su seconda richiesta
            StaffRequest reqCook = em.addShiftAndRequestStaff(day, Time.valueOf("11:30:00"), Time.valueOf("15:30:00"), "Cuoco sala", 1);
            em.assignStaffMember(reqCook, cook, "Impiattamento finale");
            assertEquals(cook, reqCook.getAssignedUser());
            assertTrue(reqCook.getShift().isBooked(cook));

            // Rimozione richiesta in eccesso e liberazione del personale dal turno
            em.removeStaffRequest(reqCook);
            assertFalse(srv.getStaffRequests().contains(reqCook));
            assertFalse(reqCook.getShift().isBooked(cook));
        }

        @Test
        @DisplayName("ALT: quantità <= 0, ruolo vuoto e assegnazione utente senza ruolo SERVIZIO/CUOCO")
        void testStaffRequestExceptions() throws UseCaseLogicException {
            Date day = Date.valueOf("2026-10-02");
            em.createEvent("Buffet", "Torino", day, day);
            em.createService("Aperitivo", day, Time.valueOf("18:00:00"), Time.valueOf("20:00:00"), "Sala");

            assertThrows(UseCaseLogicException.class, () ->
                    em.addShiftAndRequestStaff(day, Time.valueOf("17:00:00"), Time.valueOf("21:00:00"), "Cameriere", 0));
            assertThrows(UseCaseLogicException.class, () ->
                    em.addShiftAndRequestStaff(day, Time.valueOf("17:00:00"), Time.valueOf("21:00:00"), "  ", 2));

            StaffRequest req = em.addShiftAndRequestStaff(day, Time.valueOf("17:00:00"), Time.valueOf("21:00:00"), "Cameriere", 2);

            // Carlin è solo Organizzatore (né SERVIZIO né CUOCO)
            assertThrows(UseCaseLogicException.class, () ->
                    em.assignStaffMember(req, organizer, "Cameriere"));
            assertThrows(UseCaseLogicException.class, () ->
                    em.assignStaffMember(req, null, "Cameriere"));
        }
    }

    @Nested
    @DisplayName("1b. Eventi Ricorrenti (defineRecurrence, modifyRecurrence, modifyRecurrenceEvents, deleteRecurrenceEvents)")
    class RecurrenceTests {

        @Test
        @DisplayName("Definizione ricorrenza, modifica singola istanza, estensione e riduzione via Observer")
        void testRecurrenceLifecycle() throws UseCaseLogicException {
            Date start = Date.valueOf("2026-10-05");
            Event ev = em.createEvent("Coffee Break Settimanale", "Sede Torino", start, start);

            em.defineRecurrence("WEEKLY", Date.valueOf("2026-10-19")); // 3 istanze
            assertTrue(ev.isRecurring());
            assertEquals(3, ev.getInstances().size());

            // Modifica singola istanza
            EventInstance second = ev.getInstances().get(1);
            em.modifyRecurrenceEvents(second, false, "Sede Milano", "annullato");
            assertTrue(second.isModified());
            assertEquals("Sede Milano", second.getCustomLocation());
            assertEquals(Event.State.ANNULLATO, second.getInstanceState());

            // Modifica ricorrenza a 4 occorrenze preservando l'istanza modificata
            em.modifyRecurrence("WEEKLY", 4);
            assertEquals(4, ev.getInstances().size());
            assertTrue(ev.getInstances().get(1).isModified());
            assertEquals("Sede Milano", ev.getInstances().get(1).getCustomLocation());

            // Riduzione ricorrenza a 2 occorrenze: elimina le 2 istanze in eccesso dal DB tramite Observer
            em.modifyRecurrence("WEEKLY", 2);
            assertEquals(2, ev.getInstances().size());
            Event reloaded = Event.loadById(ev.getId());
            assertEquals(2, reloaded.getInstances().size());
        }

        @Test
        @DisplayName("modifyRecurrenceEvents e deleteRecurrenceEvents con applyToAll = true e rispetto istanze terminate")
        void testModifyAndDeleteAllRecurrenceInstances() throws UseCaseLogicException {
            Date start = Date.valueOf("2026-11-01");
            Event ev = em.createEvent("Corso Serale", "Sede A", start, start);
            em.defineRecurrence("WEEKLY", 3);

            // Impostiamo la prima istanza a "terminato"
            EventInstance first = ev.getInstances().get(0);
            em.modifyRecurrenceEvents(first, false, null, "terminato");
            assertEquals(Event.State.TERMINATO, first.getInstanceState());

            // Modifica singola su istanza già terminata -> eccezione
            assertThrows(UseCaseLogicException.class, () ->
                    em.modifyRecurrenceEvents(first, false, "Sede B", "annullato"));

            // Eliminazione singola su istanza già terminata -> eccezione
            assertThrows(UseCaseLogicException.class, () ->
                    em.deleteRecurrenceEvents(first, false));

            // applyToAll = true aggiorna solo le istanze non terminate
            em.modifyRecurrenceEvents(null, true, "Sede Nuova", "annullato");
            assertEquals(Event.State.TERMINATO, ev.getInstances().get(0).getInstanceState());
            assertEquals("Sede A", ev.getInstances().get(0).getCustomLocation());
            assertEquals(Event.State.ANNULLATO, ev.getInstances().get(1).getInstanceState());
            assertEquals("Sede Nuova", ev.getInstances().get(1).getCustomLocation());

            // deleteRecurrenceEvents(applyToAll = true) elimina solo le eliminabili, preservando quella terminata
            em.deleteRecurrenceEvents(null, true);
            assertEquals(1, ev.getInstances().size());
            assertEquals(first, ev.getInstances().get(0));
            assertEquals(1, Event.loadById(ev.getId()).getInstances().size());
        }
    }

    @Nested
    @DisplayName("8, *a, *b, *c. Conferma, Chiusura, Annullamento ed Eliminazione")
    class EventLifecycleTests {

        @Test
        @DisplayName("confirmEvent, terminateEvent, cancelEvent e deleteEvent con relative eccezioni")
        void testLifecycleAndExceptions() throws UseCaseLogicException {
            Date day = Date.valueOf("2026-12-01");
            Event ev = em.createEvent("Conferenza Annuale", "Lingotto", day, day);

            em.confirmEvent();
            assertEquals(Event.State.PROGRAMMATO, ev.getEventState());

            em.terminateEvent("Ottima riuscita", "report_finale.pdf");
            assertEquals(Event.State.TERMINATO, ev.getEventState());
            assertEquals("Ottima riuscita", ev.getNotes());
            assertEquals("report_finale.pdf", ev.getDocumentation());

            // Evento già terminato: non confermabile, non annullabile, non eliminabile
            assertThrows(UseCaseLogicException.class, () -> em.confirmEvent());
            assertThrows(UseCaseLogicException.class, () -> em.cancelEvent());
            assertThrows(UseCaseLogicException.class, () -> em.deleteEvent(ev));

            // Evento annullato: non terminabile
            Event ev2 = em.createEvent("Aperitivo", "Piazza San Carlo", day, day);
            em.cancelEvent();
            assertEquals(Event.State.ANNULLATO, ev2.getEventState());
            assertThrows(UseCaseLogicException.class, () -> em.terminateEvent("Note", "doc.pdf"));

            // Evento in lavorazione: eliminabile definitivamente
            Event ev3 = em.createEvent("Bozza Provvisoria", "Ufficio", day, day);
            assertTrue(em.deleteEvent(ev3));
            assertNull(Event.loadById(ev3.getId()));
        }
    }
}