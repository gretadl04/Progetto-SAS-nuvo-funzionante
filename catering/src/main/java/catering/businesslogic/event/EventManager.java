package catering.businesslogic.event;

import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.shift.Shift;
import catering.businesslogic.user.User;

public class EventManager {

    private Event currentEvent;
    private Service currentService;
    private ArrayList<EventReceiver> eventReceivers;

    public EventManager() {
        this.eventReceivers = new ArrayList<>();
    }

    private User checkOrganizerRole() throws UseCaseLogicException {
        User user = CatERing.getInstance().getUserManager().getCurrentUser();
        if (user == null || !user.isOrganizer()) {
            throw new UseCaseLogicException("Operazione consentita solo a un Organizzatore.");
        }
        return user;
    }

    // Passo 1: Creazione scheda nuovo evento
    public Event createEvent(String name, String location, Date dateStart, Date dateEnd) throws UseCaseLogicException {
        return createEvent(name, null, location, dateStart, dateEnd, 0, null);
    }

    public Event createEvent(String name, String client, String location, Date dateStart, Date dateEnd, int expectedParticipants, String notes) throws UseCaseLogicException {
        User organizer = checkOrganizerRole();
        if (name == null || name.trim().isEmpty()) {
            throw new UseCaseLogicException("Il nome dell'evento è obbligatorio.");
        }
        if (dateStart != null && dateEnd != null && dateEnd.before(dateStart)) {
            throw new UseCaseLogicException("La data di fine non può precedere la data di inizio.");
        }
        if (expectedParticipants < 0) {
            throw new UseCaseLogicException("Il numero di partecipanti non può essere negativo.");
        }

        Event e = new Event(name);
        e.setOrganizer(organizer);
        e.setClient(client);
        e.setLocation(location);
        e.setDateStart(dateStart);
        e.setDateEnd(dateEnd);
        e.setExpectedParticipants(expectedParticipants);
        e.setNotes(notes);
        e.setState("in lavorazione");

        this.currentEvent = e;
        this.currentService = null;
        notifyEventCreated(e);
        return e;
    }

    // Metodo compatibile col template base
    public Event createEvent(String name, Date dateStart, Date dateEnd, User chef) throws UseCaseLogicException {
        Event e = createEvent(name, "Sede principale", dateStart, dateEnd);
        if (chef != null) {
            assignChef(chef);
        }
        return e;
    }

    // Estensione 1a: Selezione di un evento esistente
    public Event selectEvent(Event e) throws UseCaseLogicException {
        checkOrganizerRole();
        if (e == null) {
            throw new UseCaseLogicException("Evento non valido.");
        }
        this.currentEvent = e;
        this.currentService = (!e.getServices().isEmpty()) ? e.getServices().get(0) : null;
        return this.currentEvent;
    }

    // Modifica caratteristiche della scheda evento (con vincolo ±30% partecipanti e blocco aggiunta giorni se "in corso")
    public void modifyEvent(String newName, String newLocation, Date newDateStart, Date newDateEnd, int newExpectedParticipants, String newNotes) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento selezionato.");
        }
        if ("terminato".equalsIgnoreCase(currentEvent.getState()) || "annullato".equalsIgnoreCase(currentEvent.getState())) {
            throw new UseCaseLogicException("Impossibile modificare un evento terminato o annullato.");
        }
        if (newDateStart != null && newDateEnd != null && newDateEnd.before(newDateStart)) {
            throw new UseCaseLogicException("La data di fine non può precedere la data di inizio.");
        }

        if ("in corso".equalsIgnoreCase(currentEvent.getState())) {
            if (newDateStart != null && currentEvent.getDateStart() != null && !newDateStart.equals(currentEvent.getDateStart())) {
                throw new UseCaseLogicException("Impossibile modificare le date di un evento dopo l'inizio dei lavori.");
            }
            if (newDateEnd != null && currentEvent.getDateEnd() != null && !newDateEnd.equals(currentEvent.getDateEnd())) {
                throw new UseCaseLogicException("Impossibile aggiungere giorni a un evento dopo l'inizio dei lavori.");
            }
            int oldParticipants = currentEvent.getExpectedParticipants();
            if (oldParticipants > 0 && newExpectedParticipants > 0) {
                double minAllowed = oldParticipants * 0.70;
                double maxAllowed = oldParticipants * 1.30;
                if (newExpectedParticipants < minAllowed || newExpectedParticipants > maxAllowed) {
                    throw new UseCaseLogicException("Dopo l'inizio dei lavori il numero di partecipanti non può variare oltre il 30%.");
                }
            }
        }

        if (newName != null && !newName.trim().isEmpty()) currentEvent.setName(newName);
        if (newLocation != null) currentEvent.setLocation(newLocation);
        if (newDateStart != null) currentEvent.setDateStart(newDateStart);
        if (newDateEnd != null) currentEvent.setDateEnd(newDateEnd);
        if (newExpectedParticipants > 0) currentEvent.setExpectedParticipants(newExpectedParticipants);
        if (newNotes != null) currentEvent.setNotes(newNotes);

        notifyEventUpdated(currentEvent);
    }

    // Passo 3: Assegnazione Chef all'evento
    public void assignChef(User chef) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento selezionato.");
        }
        if ("terminato".equalsIgnoreCase(currentEvent.getState()) || "annullato".equalsIgnoreCase(currentEvent.getState())) {
            throw new UseCaseLogicException("Impossibile assegnare uno chef a un evento terminato o annullato.");
        }
        if (chef == null || !chef.isChef()) {
            throw new UseCaseLogicException("L'utente indicato non ha il ruolo di Chef.");
        }
        currentEvent.setChef(chef);
        notifyEventUpdated(currentEvent);
    }

    // Passo 5: Definizione di un servizio
    public Service createService(String name, Date date, Time timeStart, Time timeEnd, String location) throws UseCaseLogicException {
        return createService(name, date, timeStart, timeEnd, location, currentEvent != null ? currentEvent.getExpectedParticipants() : 0);
    }

    public Service createService(String name, Date date, Time timeStart, Time timeEnd, String location, int expectedParticipants) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento in corso di definizione.");
        }
        if ("in corso".equalsIgnoreCase(currentEvent.getState()) ||
            "terminato".equalsIgnoreCase(currentEvent.getState()) ||
            "annullato".equalsIgnoreCase(currentEvent.getState())) {
            throw new UseCaseLogicException("Non è possibile aggiungere nuovi servizi dopo l'inizio dei lavori o a evento chiuso.");
        }

        Service s = new Service(name);
        s.setEventId(currentEvent.getId());
        s.setDate(date != null ? date : currentEvent.getDateStart());
        s.setTimeStart(timeStart);
        s.setTimeEnd(timeEnd);
        s.setLocation(location != null ? location : currentEvent.getLocation());
        s.setExpectedParticipants(expectedParticipants);

        currentEvent.addService(s);
        this.currentService = s;
        notifyServiceCreated(currentEvent, s);
        return s;
    }

    // Metodo rapido per aggiungere un servizio indicando solo la tipologia
    public Service addService(String serviceType) throws UseCaseLogicException {
        return createService(serviceType, currentEvent != null ? currentEvent.getDateStart() : null,
                Time.valueOf("12:00:00"), Time.valueOf("15:00:00"),
                currentEvent != null ? currentEvent.getLocation() : null);
    }

    // Modifica dei partecipanti o dettagli di un servizio (rispettando il limite del ±30% se "in corso")
    public void modifyServiceParticipants(Service service, int newParticipants) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || service == null || !currentEvent.containsService(service)) {
            throw new UseCaseLogicException("Servizio non appartenente all'evento corrente.");
        }
        if (newParticipants <= 0) {
            throw new UseCaseLogicException("Il numero di partecipanti deve essere positivo.");
        }
        if ("in corso".equalsIgnoreCase(currentEvent.getState()) && service.getExpectedParticipants() > 0) {
            int oldP = service.getExpectedParticipants();
            if (newParticipants < oldP * 0.70 || newParticipants > oldP * 1.30) {
                throw new UseCaseLogicException("Dopo l'inizio dei lavori il numero di partecipanti del servizio non può variare oltre il 30%.");
            }
        }
        service.setExpectedParticipants(newParticipants);
        notifyServiceUpdated(service);
    }

    // Eliminazione o annullamento di un singolo servizio
    public void deleteOrCancelService(Service service) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || service == null || !currentEvent.containsService(service)) {
            throw new UseCaseLogicException("Servizio non appartenente all'evento corrente.");
        }
        if ("terminato".equalsIgnoreCase(currentEvent.getState())) {
            throw new UseCaseLogicException("Impossibile rimuovere un servizio di un evento terminato.");
        }
        if ("in corso".equalsIgnoreCase(currentEvent.getState())) {
            service.setState("annullato");
            notifyServiceUpdated(service);
        } else {
            currentEvent.removeService(service);
            if (service.equals(currentService)) {
                currentService = null;
            }
            notifyServiceDeleted(currentEvent, service);
        }
    }

    // Passo 7: Definisce turno e richiede il personale specificando ruolo e quantità
    public StaffRequest addShiftAndRequestStaff(Date date, Time startTime, Time endTime, String role, int quantity) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || currentService == null) {
            throw new UseCaseLogicException("Devi prima selezionare un evento e un servizio.");
        }
        if (quantity <= 0 || role == null || role.trim().isEmpty()) {
            throw new UseCaseLogicException("Ruolo e quantità (>0) sono obbligatori.");
        }

        Shift shift = CatERing.getInstance().getShiftManager().createShift(
                date != null ? date : currentService.getDate(),
                startTime,
                endTime
        );

        StaffRequest sr = new StaffRequest(currentService.getId(), shift, role, quantity);
        currentService.addStaffRequest(sr);
        notifyStaffRequested(currentService, sr);
        return sr;
    }

    // Assegnazione di un membro specifico del personale (di servizio o cuoco di supporto) a una richiesta
    public void assignStaffMember(StaffRequest request, User staffMember, String specificRole) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || currentService == null || request == null || !currentService.getStaffRequests().contains(request)) {
            throw new UseCaseLogicException("Richiesta di personale non valida per il servizio corrente.");
        }
        if (staffMember == null || (!staffMember.isServizio() && !staffMember.isCook())) {
            throw new UseCaseLogicException("L'utente selezionato non appartiene al personale di servizio o cucina.");
        }
        if (request.getShift() != null) {
            CatERing.getInstance().getShiftManager().bookUserForShift(request.getShift(), staffMember);
        }
        request.setAssignedUser(staffMember);
        if (specificRole != null && !specificRole.trim().isEmpty()) {
            request.setRole(specificRole);
        }
        notifyStaffRequestUpdated(currentService, request);
    }

    // Liberazione del personale in eccesso (rimozione richiesta di personale)
    public void removeStaffRequest(StaffRequest request) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || currentService == null || request == null || !currentService.getStaffRequests().contains(request)) {
            throw new UseCaseLogicException("Richiesta di personale non trovata nel servizio corrente.");
        }
        if (request.getShift() != null && request.getAssignedUser() != null) {
            CatERing.getInstance().getShiftManager().removeUserFromShift(request.getShift(), request.getAssignedUser());
        }
        currentService.removeStaffRequest(request);
        notifyStaffRequestDeleted(currentService, request);
    }

    // Passo 9: Conferma dell'evento
    public void confirmEvent() throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento in corso.");
        }
        if ("terminato".equalsIgnoreCase(currentEvent.getState()) || "annullato".equalsIgnoreCase(currentEvent.getState())) {
            throw new UseCaseLogicException("Impossibile confermare un evento terminato o annullato.");
        }
        currentEvent.setState("programmato");
        notifyEventUpdated(currentEvent);
    }

    // Estensione 1b: Definizione ricorrenza tramite data di fine
    public void defineRecurrence(String frequency, Date endRecurrence) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento in corso.");
        }
        if (endRecurrence == null || (currentEvent.getDateStart() != null && endRecurrence.before(currentEvent.getDateStart()))) {
            throw new UseCaseLogicException("Data di fine ricorrenza non valida.");
        }
        currentEvent.generateRecurrenceInstances(frequency, endRecurrence);
        notifyEventUpdated(currentEvent);
    }

    // Estensione 1b: Definizione ricorrenza tramite numero di occorrenze
    public void defineRecurrence(String frequency, int occurrences) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento in corso.");
        }
        if (occurrences <= 0) {
            throw new UseCaseLogicException("Il numero di ripetizioni deve essere maggiore di zero.");
        }
        currentEvent.generateRecurrenceInstances(frequency, occurrences);
        notifyEventUpdated(currentEvent);
    }

    // Estensione 1b.2: Modifica della ricorrenza in sé (preservando le istanze già in calendario)
    public void modifyRecurrence(String newFrequency, Date newEndRecurrence) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || !currentEvent.isRecurring()) {
            throw new UseCaseLogicException("L'evento corrente non è un evento ricorrente.");
        }
        if (newEndRecurrence == null || (currentEvent.getDateStart() != null && newEndRecurrence.before(currentEvent.getDateStart()))) {
            throw new UseCaseLogicException("Nuova data di fine ricorrenza non valida.");
        }
        currentEvent.updateRecurrenceInstances(newFrequency, newEndRecurrence, null);
        notifyEventUpdated(currentEvent);
    }

    public void modifyRecurrence(String newFrequency, int newOccurrences) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || !currentEvent.isRecurring()) {
            throw new UseCaseLogicException("L'evento corrente non è un evento ricorrente.");
        }
        if (newOccurrences <= 0) {
            throw new UseCaseLogicException("Il nuovo numero di occorrenze deve essere maggiore di zero.");
        }
        currentEvent.updateRecurrenceInstances(newFrequency, null, newOccurrences);
        notifyEventUpdated(currentEvent);
    }

    // Estensione 1b.1: Modifica / Annullamento di una singola istanza o di tutte le istanze della ricorrenza
    public void modifyRecurrenceEvents(EventInstance targetInstance, boolean applyToAll, String newLocation, String newState) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || !currentEvent.isRecurring()) {
            throw new UseCaseLogicException("L'evento corrente non è un evento ricorrente.");
        }
        if (applyToAll) {
            if (newLocation != null) currentEvent.setLocation(newLocation);
            if (newState != null && !"terminato".equalsIgnoreCase(currentEvent.getState())) {
                currentEvent.setState(newState);
            }
            for (EventInstance inst : currentEvent.getInstances()) {
                if ("terminato".equalsIgnoreCase(inst.getState())) {
                    continue; // Non modifica né annulla istanze già terminate
                }
                if (newLocation != null) inst.setCustomLocation(newLocation);
                if (newState != null) inst.setState(newState);
                inst.updateInstance();
            }
        } else {
            if (targetInstance == null || !currentEvent.getInstances().contains(targetInstance)) {
                throw new UseCaseLogicException("Istanza ricorrente non valida.");
            }
            if ("terminato".equalsIgnoreCase(targetInstance.getState())) {
                throw new UseCaseLogicException("Impossibile modificare o annullare un'istanza già terminata.");
            }
            if (newLocation != null) targetInstance.setCustomLocation(newLocation);
            if (newState != null) targetInstance.setState(newState);
            targetInstance.setModified(true);
            targetInstance.updateInstance();
        }
        notifyEventUpdated(currentEvent);
    }

    // Estensione 1b.1: Eliminazione di una singola istanza o di tutte le istanze eliminabili della ricorrenza
    public void deleteRecurrenceEvents(EventInstance targetInstance, boolean applyToAll) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || !currentEvent.isRecurring()) {
            throw new UseCaseLogicException("L'evento corrente non è un evento ricorrente.");
        }
        if (applyToAll) {
            ArrayList<EventInstance> toRemove = new ArrayList<>();
            for (EventInstance inst : currentEvent.getInstances()) {
                if (!"in corso".equalsIgnoreCase(inst.getState()) && !"terminato".equalsIgnoreCase(inst.getState())) {
                    inst.deleteInstance();
                    toRemove.add(inst);
                }
            }
            currentEvent.getInstances().removeAll(toRemove);
            notifyEventUpdated(currentEvent);
        } else {
            if (targetInstance == null || !currentEvent.getInstances().contains(targetInstance)) {
                throw new UseCaseLogicException("Istanza ricorrente non valida.");
            }
            if ("in corso".equalsIgnoreCase(targetInstance.getState()) || "terminato".equalsIgnoreCase(targetInstance.getState())) {
                throw new UseCaseLogicException("Impossibile eliminare un'istanza in corso o già terminata.");
            }
            targetInstance.deleteInstance();
            currentEvent.getInstances().remove(targetInstance);
            notifyEventUpdated(currentEvent);
        }
    }

    // Estensione 3a: Approvazione menù proposto dallo Chef con eventuali proposte di modifica (emendamenti)
    public void approveMenu(Service service, Menu menu, String proposedModifications) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || service == null || !currentEvent.containsService(service)) {
            throw new UseCaseLogicException("Servizio non appartenente all'evento corrente.");
        }
        if (menu == null) {
            throw new UseCaseLogicException("Menù non valido.");
        }
        service.approveMenu(menu, proposedModifications);
        notifyServiceUpdated(service);

        // L'approvazione del menù dà il via ai lavori: l'evento passa nello stato "in corso"
        if ("in lavorazione".equalsIgnoreCase(currentEvent.getState()) || "programmato".equalsIgnoreCase(currentEvent.getState())) {
            currentEvent.setState("in corso");
            notifyEventUpdated(currentEvent);
        }
    }

    // Estensione *a: Terminare un evento aggiungendo note e documentazione
    public void terminateEvent(String notes, String documentation) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento selezionato.");
        }
        if ("annullato".equalsIgnoreCase(currentEvent.getState())) {
            throw new UseCaseLogicException("Impossibile terminare un evento già annullato.");
        }
        currentEvent.setState("terminato");
        currentEvent.setNotes(notes);
        currentEvent.setDocumentation(documentation);
        notifyEventUpdated(currentEvent);
    }

    // Estensione *b: Annullare un evento
    public void cancelEvent() throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento selezionato.");
        }
        if ("terminato".equalsIgnoreCase(currentEvent.getState())) {
            throw new UseCaseLogicException("Non è possibile annullare un evento già terminato.");
        }
        currentEvent.setState("annullato");
        notifyEventUpdated(currentEvent);
    }

    // Estensione *b: Eliminare un evento (consentito solo prima dell'avvio dei lavori)
    public boolean deleteEvent(Event e) throws UseCaseLogicException {
        checkOrganizerRole();
        if (e == null) {
            throw new UseCaseLogicException("Evento non valido.");
        }
        if ("in corso".equalsIgnoreCase(e.getState()) || "terminato".equalsIgnoreCase(e.getState())) {
            throw new UseCaseLogicException("Impossibile eliminare un evento in corso o già terminato.");
        }
        notifyEventDeleted(e);
        if (currentEvent != null && currentEvent.equals(e)) {
            currentEvent = null;
            currentService = null;
        }
        return true;
    }

    public ArrayList<Event> getAllEvents() {
        return Event.loadAllEvents();
    }

    public Event getCurrentEvent() { return currentEvent; }
    public Service getCurrentService() { return currentService; }
    public void setCurrentService(Service currentService) { this.currentService = currentService; }

    // Observer Pattern
    public void addEventReceiver(EventReceiver er) {
        if (!eventReceivers.contains(er)) eventReceivers.add(er);
    }

    public void removeEventReceiver(EventReceiver er) {
        eventReceivers.remove(er);
    }

    private void notifyEventCreated(Event e) {
        for (EventReceiver er : eventReceivers) er.updateEventCreated(e);
    }

    private void notifyEventUpdated(Event e) {
        for (EventReceiver er : eventReceivers) er.updateEventUpdated(e);
    }

    private void notifyEventDeleted(Event e) {
        for (EventReceiver er : eventReceivers) er.updateEventDeleted(e);
    }

    private void notifyServiceCreated(Event e, Service s) {
        for (EventReceiver er : eventReceivers) er.updateServiceCreated(e, s);
    }

    private void notifyServiceUpdated(Service s) {
        for (EventReceiver er : eventReceivers) er.updateServiceUpdated(s);
    }

    private void notifyServiceDeleted(Event e, Service s) {
        for (EventReceiver er : eventReceivers) er.updateServiceDeleted(e, s);
    }

    private void notifyStaffRequested(Service s, StaffRequest sr) {
        for (EventReceiver er : eventReceivers) er.updateStaffRequested(s, sr);
    }

    private void notifyStaffRequestUpdated(Service s, StaffRequest sr) {
        for (EventReceiver er : eventReceivers) er.updateStaffRequestUpdated(s, sr);
    }

    private void notifyStaffRequestDeleted(Service s, StaffRequest sr) {
        for (EventReceiver er : eventReceivers) er.updateStaffRequestDeleted(s, sr);
    }
}