package catering.businesslogic.event;

import java.sql.Date;
import java.sql.Time;
import java.util.ArrayList;

import catering.businesslogic.CatERing;
import catering.businesslogic.UseCaseLogicException;
import catering.businesslogic.menu.Menu;
import catering.businesslogic.shift.Shift;
import catering.businesslogic.user.User;

/**
 * GRASP Controller for the "Gestire gli eventi" use case.
 * Enforces Organizer authorization, domain preconditions, and state-transition rules,
 * delegating persistence to registered {@link EventReceiver} observers.
 */
public class EventManager {

    private Event currentEvent;
    private Service currentService;
    private ArrayList<EventReceiver> eventReceivers;

    /**
     * Initializes the manager with an empty list of event receivers.
     */
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

    /**
     * Creates a new event with basic details.
     *
     * @param name      the event name
     * @param location  the event location
     * @param dateStart the start date
     * @param dateEnd   the end date
     * @return the newly created {@link Event}
     * @throws UseCaseLogicException if the caller is not an Organizer or parameters are invalid
     */
    public Event createEvent(String name, String location, Date dateStart, Date dateEnd) throws UseCaseLogicException {
        return createEvent(name, null, location, dateStart, dateEnd, 0, null);
    }

    /**
     * Creates a new event with full specification details.
     *
     * @param name                 the event name
     * @param client               the client commissioning the event
     * @param location             the event location
     * @param dateStart            the start date
     * @param dateEnd              the end date
     * @param expectedParticipants the expected number of participants (must be {@code >= 0})
     * @param notes                optional event notes
     * @return the newly created {@link Event}
     * @throws UseCaseLogicException if the caller is not an Organizer or parameters are invalid
     */
    public Event createEvent(String name, String client, String location, Date dateStart, Date dateEnd,
                             int expectedParticipants, String notes) throws UseCaseLogicException {
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
        e.setState(Event.State.IN_LAVORAZIONE);

        this.currentEvent = e;
        this.currentService = null;
        notifyEventCreated(e);
        return e;
    }

    /**
     * Creates a new event and optionally assigns a Chef.
     *
     * @param name      the event name
     * @param dateStart the start date
     * @param dateEnd   the end date
     * @param chef      optional Chef to assign
     * @return the newly created {@link Event}
     * @throws UseCaseLogicException if preconditions fail
     */
    public Event createEvent(String name, Date dateStart, Date dateEnd, User chef) throws UseCaseLogicException {
        Event e = createEvent(name, "Sede principale", dateStart, dateEnd);
        if (chef != null) {
            assignChef(chef);
        }
        return e;
    }

    /**
     * Selects an existing event as the current event under management.
     *
     * @param e the event to select
     * @return the selected {@link Event}
     * @throws UseCaseLogicException if the caller is not an Organizer or {@code e} is {@code null}
     */
    public Event selectEvent(Event e) throws UseCaseLogicException {
        checkOrganizerRole();
        if (e == null) {
            throw new UseCaseLogicException("Evento non valido.");
        }
        this.currentEvent = e;
        this.currentService = (!e.getServices().isEmpty()) ? e.getServices().get(0) : null;
        return this.currentEvent;
    }

    /**
     * Selects a service belonging to the current event.
     *
     * @param service the service to select
     * @return the selected {@link Service}
     * @throws UseCaseLogicException if the caller is not an Organizer, no event is active,
     *                               or the service does not belong to the current event
     */
    public Service selectService(Service service) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || service == null || !currentEvent.containsService(service)) {
            throw new UseCaseLogicException("Servizio non appartenente all'evento corrente.");
        }
        this.currentService = service;
        return this.currentService;
    }

    /**
     * Modifies the attributes of the current event.
     * When the event is {@link Event.State#IN_CORSO}, dates cannot be modified and
     * participants cannot deviate by more than ±30% from the initial value.
     *
     * @param newName                 optional new name
     * @param newLocation             optional new location
     * @param newDateStart            optional new start date
     * @param newDateEnd              optional new end date
     * @param newExpectedParticipants optional new participant count (ignored if {@code <= 0})
     * @param newNotes                optional updated notes
     * @throws UseCaseLogicException if preconditions or state rules are violated
     */
    public void modifyEvent(String newName, String newLocation, Date newDateStart, Date newDateEnd,
                            int newExpectedParticipants, String newNotes) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento selezionato.");
        }
        Event.State state = currentEvent.getEventState();
        if (!state.canBeModified()) {
            throw new UseCaseLogicException("Impossibile modificare un evento terminato o annullato.");
        }
        if (newDateStart != null && newDateEnd != null && newDateEnd.before(newDateStart)) {
            throw new UseCaseLogicException("La data di fine non può precedere la data di inizio.");
        }

        if (state.isInProgress()) {
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

    /**
     * Assigns a Chef to the current event.
     *
     * @param chef the user with {@link User.Role#CHEF} role to assign
     * @throws UseCaseLogicException if preconditions fail or the user is not a Chef
     */
    public void assignChef(User chef) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento selezionato.");
        }
        if (!currentEvent.getEventState().canBeModified()) {
            throw new UseCaseLogicException("Impossibile assegnare uno chef a un evento terminato o annullato.");
        }
        if (chef == null || !chef.isChef()) {
            throw new UseCaseLogicException("L'utente indicato non ha il ruolo di Chef.");
        }
        currentEvent.setChef(chef);
        notifyEventUpdated(currentEvent);
    }

    /**
     * Creates a new service for the current event inheriting its expected participants.
     *
     * @param name      the service name
     * @param date      the service date (defaults to event start date if {@code null})
     * @param timeStart the start time
     * @param timeEnd   the end time
     * @param location  the service location (defaults to event location if {@code null})
     * @return the newly created {@link Service}
     * @throws UseCaseLogicException if preconditions fail or the event cannot accept new services
     */
    public Service createService(String name, Date date, Time timeStart, Time timeEnd, String location)
            throws UseCaseLogicException {
        return createService(name, date, timeStart, timeEnd, location,
                currentEvent != null ? currentEvent.getExpectedParticipants() : 0);
    }

    /**
     * Creates a new service for the current event with an explicit participant count.
     *
     * @param name                 the service name
     * @param date                 the service date
     * @param timeStart            the start time
     * @param timeEnd              the end time
     * @param location             the service location
     * @param expectedParticipants expected number of participants
     * @return the newly created {@link Service}
     * @throws UseCaseLogicException if preconditions fail or the event is already in progress/closed
     */
    public Service createService(String name, Date date, Time timeStart, Time timeEnd, String location,
                                 int expectedParticipants) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento in corso di definizione.");
        }
        if (!currentEvent.getEventState().canAddServices()) {
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

    /**
     * Convenience method to add a service specifying only its type/name with default hours.
     *
     * @param serviceType the type or name of the service
     * @return the newly created {@link Service}
     * @throws UseCaseLogicException if preconditions fail
     */
    public Service addService(String serviceType) throws UseCaseLogicException {
        return createService(serviceType, currentEvent != null ? currentEvent.getDateStart() : null,
                Time.valueOf("12:00:00"), Time.valueOf("15:00:00"),
                currentEvent != null ? currentEvent.getLocation() : null);
    }

    /**
     * Modifies the expected participants of a service, enforcing the ±30% rule when the event is in progress.
     *
     * @param service         the target service
     * @param newParticipants the updated participant count
     * @throws UseCaseLogicException if preconditions fail or the ±30% rule is violated
     */
    public void modifyServiceParticipants(Service service, int newParticipants) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || service == null || !currentEvent.containsService(service)) {
            throw new UseCaseLogicException("Servizio non appartenente all'evento corrente.");
        }
        if (newParticipants <= 0) {
            throw new UseCaseLogicException("Il numero di partecipanti deve essere positivo.");
        }
        if (currentEvent.getEventState().isInProgress() && service.getExpectedParticipants() > 0) {
            int oldP = service.getExpectedParticipants();
            if (newParticipants < oldP * 0.70 || newParticipants > oldP * 1.30) {
                throw new UseCaseLogicException("Dopo l'inizio dei lavori il numero di partecipanti del servizio non può variare oltre il 30%.");
            }
        }
        service.setExpectedParticipants(newParticipants);
        notifyServiceUpdated(service);
    }

    /**
     * Deletes a service before work starts, or marks it as {@link Service.State#ANNULLATO} if the event is in progress.
     *
     * @param service the service to delete or cancel
     * @throws UseCaseLogicException if preconditions fail or the event is already terminated
     */
    public void deleteOrCancelService(Service service) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || service == null || !currentEvent.containsService(service)) {
            throw new UseCaseLogicException("Servizio non appartenente all'evento corrente.");
        }
        if (currentEvent.getEventState().isTerminated()) {
            throw new UseCaseLogicException("Impossibile rimuovere un servizio di un evento terminato.");
        }
        if (currentEvent.getEventState().isInProgress()) {
            service.setState(Service.State.ANNULLATO);
            notifyServiceUpdated(service);
        } else {
            currentEvent.removeService(service);
            if (service.equals(currentService)) {
                currentService = null;
            }
            notifyServiceDeleted(currentEvent, service);
        }
    }

    /**
     * Creates a service shift and adds a corresponding {@link StaffRequest} to the current service.
     *
     * @param date      the shift date (defaults to current service date if {@code null})
     * @param startTime the shift start time
     * @param endTime   the shift end time
     * @param role      the required role
     * @param quantity  the number of staff needed (must be {@code > 0})
     * @return the newly created {@link StaffRequest}
     * @throws UseCaseLogicException if preconditions fail or parameters are invalid
     */
    public StaffRequest addShiftAndRequestStaff(Date date, Time startTime, Time endTime, String role, int quantity)
            throws UseCaseLogicException {
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

    /**
     * Assigns a specific service or kitchen staff member to a {@link StaffRequest} and books them on the shift.
     *
     * @param request      the target staff request belonging to the current service
     * @param staffMember  the user with {@link User.Role#SERVIZIO} or {@link User.Role#CUOCO} role
     * @param specificRole optional refined role description for this assignment
     * @throws UseCaseLogicException if preconditions fail or the user lacks an eligible role
     */
    public void assignStaffMember(StaffRequest request, User staffMember, String specificRole)
            throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || currentService == null || request == null
                || !currentService.getStaffRequests().contains(request)) {
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

    /**
     * Removes an excess {@link StaffRequest} from the current service, freeing any booked staff member.
     *
     * @param request the staff request to remove
     * @throws UseCaseLogicException if preconditions fail or the request is not found
     */
    public void removeStaffRequest(StaffRequest request) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || currentService == null || request == null
                || !currentService.getStaffRequests().contains(request)) {
            throw new UseCaseLogicException("Richiesta di personale non trovata nel servizio corrente.");
        }
        if (request.getShift() != null && request.getAssignedUser() != null) {
            CatERing.getInstance().getShiftManager().removeUserFromShift(request.getShift(), request.getAssignedUser());
        }
        currentService.removeStaffRequest(request);
        notifyStaffRequestDeleted(currentService, request);
    }

    /**
     * Confirms scheduling of the current event, transitioning it to {@link Event.State#PROGRAMMATO}.
     *
     * @throws UseCaseLogicException if no event is active or the event is terminated/cancelled
     */
    public void confirmEvent() throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento in corso.");
        }
        if (!currentEvent.getEventState().canBeConfirmed()) {
            throw new UseCaseLogicException("Impossibile confermare un evento terminato o annullato.");
        }
        currentEvent.setState(Event.State.PROGRAMMATO);
        notifyEventUpdated(currentEvent);
    }

    /**
     * Defines the current event as recurring up to an end date.
     *
     * @param frequency     the recurrence frequency
     * @param endRecurrence the inclusive end date of the recurrence
     * @throws UseCaseLogicException if preconditions fail or the end date precedes start date
     */
    public void defineRecurrence(String frequency, Date endRecurrence) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento in corso.");
        }
        if (endRecurrence == null || (currentEvent.getDateStart() != null && endRecurrence.before(currentEvent.getDateStart()))) {
            throw new UseCaseLogicException("Data di fine ricorrenza non valida.");
        }
        ArrayList<EventInstance> removed = currentEvent.generateRecurrenceInstances(frequency, endRecurrence);
        notifyEventUpdated(currentEvent);
        for (EventInstance inst : removed) {
            notifyEventInstanceDeleted(inst);
        }
    }

    /**
     * Defines the current event as recurring for a fixed number of occurrences.
     *
     * @param frequency   the recurrence frequency
     * @param occurrences the number of occurrences (must be {@code > 0})
     * @throws UseCaseLogicException if preconditions fail or occurrences {@code <= 0}
     */
    public void defineRecurrence(String frequency, int occurrences) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento in corso.");
        }
        if (occurrences <= 0) {
            throw new UseCaseLogicException("Il numero di ripetizioni deve essere maggiore di zero.");
        }
        ArrayList<EventInstance> removed = currentEvent.generateRecurrenceInstances(frequency, occurrences);
        notifyEventUpdated(currentEvent);
        for (EventInstance inst : removed) {
            notifyEventInstanceDeleted(inst);
        }
    }

    /**
     * Modifies the recurrence schedule of a recurring event using a new end date,
     * preserving existing modified instances and removing excess ones via observers.
     *
     * @param newFrequency     the updated frequency
     * @param newEndRecurrence the new end date
     * @throws UseCaseLogicException if the current event is not recurring or the date is invalid
     */
    public void modifyRecurrence(String newFrequency, Date newEndRecurrence) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || !currentEvent.isRecurring()) {
            throw new UseCaseLogicException("L'evento corrente non è un evento ricorrente.");
        }
        if (newEndRecurrence == null || (currentEvent.getDateStart() != null && newEndRecurrence.before(currentEvent.getDateStart()))) {
            throw new UseCaseLogicException("Nuova data di fine ricorrenza non valida.");
        }
        ArrayList<EventInstance> removed = currentEvent.updateRecurrenceInstances(newFrequency, newEndRecurrence, null);
        notifyEventUpdated(currentEvent);
        for (EventInstance inst : removed) {
            notifyEventInstanceDeleted(inst);
        }
    }

    /**
     * Modifies the recurrence schedule of a recurring event using a new occurrence count,
     * preserving existing modified instances and removing excess ones via observers.
     *
     * @param newFrequency   the updated frequency
     * @param newOccurrences the new occurrence count (must be {@code > 0})
     * @throws UseCaseLogicException if the current event is not recurring or count {@code <= 0}
     */
    public void modifyRecurrence(String newFrequency, int newOccurrences) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || !currentEvent.isRecurring()) {
            throw new UseCaseLogicException("L'evento corrente non è un evento ricorrente.");
        }
        if (newOccurrences <= 0) {
            throw new UseCaseLogicException("Il nuovo numero di occorrenze deve essere maggiore di zero.");
        }
        ArrayList<EventInstance> removed = currentEvent.updateRecurrenceInstances(newFrequency, null, newOccurrences);
        notifyEventUpdated(currentEvent);
        for (EventInstance inst : removed) {
            notifyEventInstanceDeleted(inst);
        }
    }

    /**
     * Modifies or cancels either a single recurrence instance or all modifiable instances of the recurrence.
     * Persistence is handled strictly through Observer notifications.
     *
     * @param targetInstance the target instance (required when {@code applyToAll} is {@code false})
     * @param applyToAll     {@code true} to propagate to all non-terminated instances, {@code false} for one instance
     * @param newLocation    optional new location
     * @param newState       optional new state string
     * @throws UseCaseLogicException if preconditions fail or a targeted instance is already terminated
     */
    public void modifyRecurrenceEvents(EventInstance targetInstance, boolean applyToAll,
                                       String newLocation, String newState) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || !currentEvent.isRecurring()) {
            throw new UseCaseLogicException("L'evento corrente non è un evento ricorrente.");
        }
        Event.State parsedState = null;
        if (newState != null) {
            parsedState = Event.State.fromDbValue(newState);
            if (parsedState == null) {
                throw new UseCaseLogicException("Stato non valido: " + newState);
            }
        }

        if (applyToAll) {
            if (newLocation != null) currentEvent.setLocation(newLocation);
            if (parsedState != null && !currentEvent.getEventState().isTerminated()) {
                currentEvent.setState(parsedState);
            }
            for (EventInstance inst : currentEvent.getInstances()) {
                if (inst.getInstanceState().isTerminated()) {
                    continue;
                }
                if (newLocation != null) inst.setCustomLocation(newLocation);
                if (parsedState != null) inst.setState(parsedState);
            }
            notifyEventUpdated(currentEvent);
        } else {
            if (targetInstance == null || !currentEvent.getInstances().contains(targetInstance)) {
                throw new UseCaseLogicException("Istanza ricorrente non valida.");
            }
            if (targetInstance.getInstanceState().isTerminated()) {
                throw new UseCaseLogicException("Impossibile modificare o annullare un'istanza già terminata.");
            }
            if (newLocation != null) targetInstance.setCustomLocation(newLocation);
            if (parsedState != null) targetInstance.setState(parsedState);
            targetInstance.setModified(true);
            notifyEventInstanceUpdated(targetInstance);
        }
    }

    /**
     * Deletes either a single recurrence instance or all deletable instances of the recurrence.
     * Persistence is handled strictly through Observer notifications.
     *
     * @param targetInstance the target instance (required when {@code applyToAll} is {@code false})
     * @param applyToAll     {@code true} to delete all deletable instances, {@code false} for one instance
     * @throws UseCaseLogicException if preconditions fail or the targeted instance cannot be deleted
     */
    public void deleteRecurrenceEvents(EventInstance targetInstance, boolean applyToAll) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null || !currentEvent.isRecurring()) {
            throw new UseCaseLogicException("L'evento corrente non è un evento ricorrente.");
        }
        if (applyToAll) {
            ArrayList<EventInstance> toRemove = new ArrayList<>();
            for (EventInstance inst : currentEvent.getInstances()) {
                if (inst.getInstanceState().canBeDeleted()) {
                    toRemove.add(inst);
                }
            }
            currentEvent.getInstances().removeAll(toRemove);
            for (EventInstance inst : toRemove) {
                notifyEventInstanceDeleted(inst);
            }
        } else {
            if (targetInstance == null || !currentEvent.getInstances().contains(targetInstance)) {
                throw new UseCaseLogicException("Istanza ricorrente non valida.");
            }
            if (!targetInstance.getInstanceState().canBeDeleted()) {
                throw new UseCaseLogicException("Impossibile eliminare un'istanza in corso o già terminata.");
            }
            currentEvent.getInstances().remove(targetInstance);
            notifyEventInstanceDeleted(targetInstance);
        }
    }

    /**
     * Approves a menu for a service of the current event, optionally attaching modification proposals.
     * Approving a menu starts work and transitions the event to {@link Event.State#IN_CORSO}.
     *
     * @param service               the target service
     * @param menu                  the menu to approve
     * @param proposedModifications optional amendment notes limited to this event
     * @throws UseCaseLogicException if preconditions fail
     */
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

        if (currentEvent.getEventState().startsWorkOnMenuApproval()) {
            currentEvent.setState(Event.State.IN_CORSO);
            notifyEventUpdated(currentEvent);
        }
    }

    /**
     * Closes the current event as {@link Event.State#TERMINATO}, saving final notes and documentation.
     *
     * @param notes         closing notes
     * @param documentation reference to final documentation
     * @throws UseCaseLogicException if no event is active or the event is already cancelled
     */
    public void terminateEvent(String notes, String documentation) throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento selezionato.");
        }
        if (!currentEvent.getEventState().canBeTerminated()) {
            throw new UseCaseLogicException("Impossibile terminare un evento già annullato.");
        }
        currentEvent.setState(Event.State.TERMINATO);
        currentEvent.setNotes(notes);
        currentEvent.setDocumentation(documentation);
        notifyEventUpdated(currentEvent);
    }

    /**
     * Cancels the current event, transitioning it to {@link Event.State#ANNULLATO}.
     *
     * @throws UseCaseLogicException if no event is active or the event is already terminated
     */
    public void cancelEvent() throws UseCaseLogicException {
        checkOrganizerRole();
        if (currentEvent == null) {
            throw new UseCaseLogicException("Nessun evento selezionato.");
        }
        if (!currentEvent.getEventState().canBeCancelled()) {
            throw new UseCaseLogicException("Non è possibile annullare un evento già terminato.");
        }
        currentEvent.setState(Event.State.ANNULLATO);
        notifyEventUpdated(currentEvent);
    }

    /**
     * Permanently deletes an event that has not started work and is not terminated.
     *
     * @param e the event to delete
     * @return {@code true} upon successful deletion notification
     * @throws UseCaseLogicException if the caller is not an Organizer, {@code e} is {@code null},
     *                               or the event cannot be deleted in its current state
     */
    public boolean deleteEvent(Event e) throws UseCaseLogicException {
        checkOrganizerRole();
        if (e == null) {
            throw new UseCaseLogicException("Evento non valido.");
        }
        if (!e.getEventState().canBeDeleted()) {
            throw new UseCaseLogicException("Impossibile eliminare un evento in corso o già terminato.");
        }
        notifyEventDeleted(e);
        if (currentEvent != null && currentEvent.equals(e)) {
            currentEvent = null;
            currentService = null;
        }
        return true;
    }

    /**
     * Retrieves all events stored in the system.
     *
     * @return list of all events
     */
    public ArrayList<Event> getAllEvents() {
        return Event.loadAllEvents();
    }

    /** @return the currently selected event, or {@code null} */
    public Event getCurrentEvent() { return currentEvent; }

    /** @return the currently selected service, or {@code null} */
    public Service getCurrentService() { return currentService; }

    /**
     * Resets the in-memory selection state (primarily for test isolation).
     */
    public void clearSelection() {
        this.currentEvent = null;
        this.currentService = null;
    }

    /**
     * Registers an observer to receive event notifications.
     *
     * @param er the {@link EventReceiver} to add
     */
    public void addEventReceiver(EventReceiver er) {
        if (er != null && !eventReceivers.contains(er)) eventReceivers.add(er);
    }

    /**
     * Unregisters an observer from event notifications.
     *
     * @param er the {@link EventReceiver} to remove
     */
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

    private void notifyEventInstanceUpdated(EventInstance ei) {
        for (EventReceiver er : eventReceivers) er.updateEventInstanceUpdated(ei);
    }

    private void notifyEventInstanceDeleted(EventInstance ei) {
        for (EventReceiver er : eventReceivers) er.updateEventInstanceDeleted(ei);
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