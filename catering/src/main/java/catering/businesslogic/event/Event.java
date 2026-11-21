package catering.businesslogic.event;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Objects;

import catering.businesslogic.user.User;
import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

/**
 * Represents a catering event managed by an Organizer and assigned to a Chef.
 * Supports single events, multi-service events, and recurring events with
 * individual {@link EventInstance} occurrences.
 */
public class Event {

    /**
     * Lifecycle states of an {@link Event} and its {@link EventInstance} occurrences.
     * Encapsulates state-dependent business rules to avoid string-based conditionals.
     */
    public enum State {
        IN_LAVORAZIONE("in lavorazione"),
        PROGRAMMATO("programmato"),
        IN_CORSO("in corso"),
        TERMINATO("terminato"),
        ANNULLATO("annullato");

        private final String dbValue;

        State(String dbValue) {
            this.dbValue = dbValue;
        }

        /**
         * Returns the string representation stored in the SQLite database.
         *
         * @return the database label for this state
         */
        public String getDbValue() {
            return dbValue;
        }

        /**
         * Parses a state label from database or user input.
         *
         * @param value the state string to parse
         * @return the matching {@link State}, or {@code null} if unrecognized
         */
        public static State fromDbValue(String value) {
            if (value == null) return null;
            String normalized = value.trim();
            for (State s : values()) {
                if (s.dbValue.equalsIgnoreCase(normalized) || s.name().equalsIgnoreCase(normalized)) {
                    return s;
                }
            }
            return null;
        }

        /** @return {@code true} if the event can still be modified */
        public boolean canBeModified() {
            return this != TERMINATO && this != ANNULLATO;
        }

        /** @return {@code true} if new services can be added to the event */
        public boolean canAddServices() {
            return this == IN_LAVORAZIONE || this == PROGRAMMATO;
        }

        /** @return {@code true} if the event can be confirmed as scheduled */
        public boolean canBeConfirmed() {
            return this != TERMINATO && this != ANNULLATO;
        }

        /** @return {@code true} if the event can be closed as terminated */
        public boolean canBeTerminated() {
            return this != ANNULLATO;
        }

        /** @return {@code true} if the event can be cancelled */
        public boolean canBeCancelled() {
            return this != TERMINATO;
        }

        /** @return {@code true} if the event can be permanently deleted */
        public boolean canBeDeleted() {
            return this != IN_CORSO && this != TERMINATO;
        }

        /** @return {@code true} if approving a menu moves this event into IN_CORSO */
        public boolean startsWorkOnMenuApproval() {
            return this == IN_LAVORAZIONE || this == PROGRAMMATO;
        }

        /** @return {@code true} if this state represents an ongoing event */
        public boolean isInProgress() {
            return this == IN_CORSO;
        }

        /** @return {@code true} if this state represents a terminated event */
        public boolean isTerminated() {
            return this == TERMINATO;
        }
    }

    private int id;
    private String name;
    private Date dateStart;
    private Date dateEnd;
    private User chef;
    private User organizer;
    private String client;
    private int expectedParticipants;
    private ArrayList<Service> services;

    private String location;
    private State state;
    private String notes;
    private String documentation;

    private boolean recurring;
    private String recurrenceFrequency;
    private Date recurrenceEnd;
    private int recurrenceOccurrences;
    private ArrayList<EventInstance> instances;

    /**
     * Creates an empty event in the initial {@link State#IN_LAVORAZIONE} state.
     */
    public Event() {
        this.services = new ArrayList<>();
        this.instances = new ArrayList<>();
        this.state = State.IN_LAVORAZIONE;
        this.recurring = false;
    }

    /**
     * Creates a new event with the specified name.
     *
     * @param name the name of the event
     */
    public Event(String name) {
        this();
        this.name = name;
    }

    /** @return the database identifier of the event */
    public int getId() { return id; }

    /** @param id the database identifier to set */
    public void setId(int id) { this.id = id; }

    /** @return the event name */
    public String getName() { return name; }

    /** @param name the event name to set */
    public void setName(String name) { this.name = name; }

    /** @return the start date of the event */
    public Date getDateStart() { return dateStart; }

    /** @param dateStart the start date to set */
    public void setDateStart(Date dateStart) { this.dateStart = dateStart; }

    /** @return the end date of the event */
    public Date getDateEnd() { return dateEnd; }

    /** @param dateEnd the end date to set */
    public void setDateEnd(Date dateEnd) { this.dateEnd = dateEnd; }

    /** @return the assigned Chef, or {@code null} if unassigned */
    public User getChef() { return chef; }

    /** @return the assigned Chef's ID, or {@code 0} if unassigned */
    public int getChefId() { return chef != null ? chef.getId() : 0; }

    /** @param chef the Chef to assign to this event */
    public void setChef(User chef) { this.chef = chef; }

    /** @return the Organizer managing this event, or {@code null} */
    public User getOrganizer() { return organizer; }

    /** @return the Organizer's ID, or {@code 0} if unassigned */
    public int getOrganizerId() { return organizer != null ? organizer.getId() : 0; }

    /** @param organizer the Organizer to assign to this event */
    public void setOrganizer(User organizer) { this.organizer = organizer; }

    /** @return the client name */
    public String getClient() { return client; }

    /** @param client the client name to set */
    public void setClient(String client) { this.client = client; }

    /** @return the expected number of participants */
    public int getExpectedParticipants() { return expectedParticipants; }

    /** @param expectedParticipants the expected number of participants */
    public void setExpectedParticipants(int expectedParticipants) { this.expectedParticipants = expectedParticipants; }

    /** @return the list of services included in this event */
    public ArrayList<Service> getServices() { return services; }

    /** @param services the list of services to set */
    public void setServices(ArrayList<Service> services) { this.services = services; }

    /** @return the event location */
    public String getLocation() { return location; }

    /** @param location the event location to set */
    public void setLocation(String location) { this.location = location; }

    /** @return the typed lifecycle state of this event */
    public State getEventState() { return state; }

    /** @return the string label of the event state */
    public String getState() { return state != null ? state.getDbValue() : null; }

    /** @param state the typed lifecycle state to set */
    public void setState(State state) {
        if (state != null) {
            this.state = state;
        }
    }

    /** @param state the string label of the state to set */
    public void setState(String state) {
        State parsed = State.fromDbValue(state);
        if (parsed != null) {
            this.state = parsed;
        }
    }

    /** @return event notes */
    public String getNotes() { return notes; }

    /** @param notes event notes to set */
    public void setNotes(String notes) { this.notes = notes; }

    /** @return documentation reference attached upon event completion */
    public String getDocumentation() { return documentation; }

    /** @param documentation documentation reference to set */
    public void setDocumentation(String documentation) { this.documentation = documentation; }

    /** @return {@code true} if this event is recurring */
    public boolean isRecurring() { return recurring; }

    /** @param recurring whether this event is recurring */
    public void setRecurring(boolean recurring) { this.recurring = recurring; }

    /** @return the recurrence frequency (e.g., DAILY, WEEKLY, MONTHLY, YEARLY) */
    public String getRecurrenceFrequency() { return recurrenceFrequency; }

    /** @param recurrenceFrequency the recurrence frequency to set */
    public void setRecurrenceFrequency(String recurrenceFrequency) { this.recurrenceFrequency = recurrenceFrequency; }

    /** @return the recurrence end date */
    public Date getRecurrenceEnd() { return recurrenceEnd; }

    /** @param recurrenceEnd the recurrence end date to set */
    public void setRecurrenceEnd(Date recurrenceEnd) { this.recurrenceEnd = recurrenceEnd; }

    /** @return the total number of recurrence occurrences */
    public int getRecurrenceOccurrences() { return recurrenceOccurrences; }

    /** @param recurrenceOccurrences the number of recurrence occurrences to set */
    public void setRecurrenceOccurrences(int recurrenceOccurrences) { this.recurrenceOccurrences = recurrenceOccurrences; }

    /** @return the list of generated {@link EventInstance} occurrences */
    public ArrayList<EventInstance> getInstances() { return instances; }

    /**
     * Adds a service to this event if not already present.
     *
     * @param service the service to add
     */
    public void addService(Service service) {
        if (services == null) services = new ArrayList<>();
        if (service != null && !services.contains(service)) {
            services.add(service);
        }
    }

    /**
     * Removes a service from this event.
     *
     * @param service the service to remove
     */
    public void removeService(Service service) {
        if (services != null) services.remove(service);
    }

    /**
     * Checks whether this event includes the given service.
     *
     * @param service the service to check
     * @return {@code true} if the service belongs to this event
     */
    public boolean containsService(Service service) {
        return services != null && services.contains(service);
    }

    private static void advanceCalendar(Calendar cal, String frequency) {
        if ("DAILY".equalsIgnoreCase(frequency) || "GIORNALIERA".equalsIgnoreCase(frequency)) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        } else if ("MONTHLY".equalsIgnoreCase(frequency) || "MENSILE".equalsIgnoreCase(frequency)) {
            cal.add(Calendar.MONTH, 1);
        } else if ("YEARLY".equalsIgnoreCase(frequency) || "ANNUALE".equalsIgnoreCase(frequency)) {
            cal.add(Calendar.YEAR, 1);
        } else {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        }
    }

    /**
     * Generates recurrence instances up to an end date.
     *
     * @param frequency     the recurrence frequency
     * @param endRecurrence the inclusive end date of the recurrence
     * @return the list of any excess instances removed from memory
     */
    public ArrayList<EventInstance> generateRecurrenceInstances(String frequency, Date endRecurrence) {
        return updateRecurrenceInstances(frequency, endRecurrence, null);
    }

    /**
     * Generates a fixed number of recurrence instances.
     *
     * @param frequency   the recurrence frequency
     * @param occurrences the number of occurrences to generate
     * @return the list of any excess instances removed from memory
     */
    public ArrayList<EventInstance> generateRecurrenceInstances(String frequency, int occurrences) {
        return updateRecurrenceInstances(frequency, null, occurrences);
    }

    /**
     * Updates recurrence instances in memory while preserving existing modified instances.
     * Returns excess instances so the controller can notify observers for persistence.
     *
     * @param frequency     the recurrence frequency
     * @param endRecurrence the recurrence end date (or {@code null} if using occurrences)
     * @param occurrences   the occurrence count (or {@code null} if using endRecurrence)
     * @return list of excess {@link EventInstance} objects removed from the event
     */
    public ArrayList<EventInstance> updateRecurrenceInstances(String frequency, Date endRecurrence, Integer occurrences) {
        ArrayList<EventInstance> removedInstances = new ArrayList<>();
        this.recurring = true;
        this.recurrenceFrequency = frequency;
        this.recurrenceEnd = endRecurrence;
        this.recurrenceOccurrences = (occurrences != null) ? occurrences : 0;

        if (this.dateStart == null) return removedInstances;
        if (endRecurrence == null && (occurrences == null || occurrences <= 0)) return removedInstances;

        ArrayList<Date> targetDates = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        cal.setTime(this.dateStart);

        if (endRecurrence != null) {
            while (!cal.getTime().after(endRecurrence)) {
                targetDates.add(new Date(cal.getTimeInMillis()));
                advanceCalendar(cal, frequency);
            }
            this.recurrenceOccurrences = targetDates.size();
        } else {
            for (int i = 0; i < occurrences; i++) {
                Date d = new Date(cal.getTimeInMillis());
                targetDates.add(d);
                this.recurrenceEnd = d;
                advanceCalendar(cal, frequency);
            }
        }

        ArrayList<EventInstance> updatedInstances = new ArrayList<>();
        for (int i = 0; i < targetDates.size(); i++) {
            Date targetDate = targetDates.get(i);
            if (i < this.instances.size()) {
                EventInstance existing = this.instances.get(i);
                if (!existing.isModified()) {
                    existing.setDate(targetDate);
                    existing.setCustomLocation(this.location);
                }
                updatedInstances.add(existing);
            } else {
                EventInstance newInst = new EventInstance(this.id, targetDate, this.location);
                updatedInstances.add(newInst);
            }
        }

        for (int i = targetDates.size(); i < this.instances.size(); i++) {
            removedInstances.add(this.instances.get(i));
        }

        this.instances = updatedInstances;
        return removedInstances;
    }

    // STATIC PERSISTENCE METHODS (called by EventPersistence observer)

    /**
     * Persists a newly created event and any initial recurrence instances to the database.
     *
     * @param e the event to persist
     */
    public static void saveNewEvent(Event e) {
        String query = "INSERT INTO Events (name, date_start, date_end, chef_id, organizer_id, client, expected_participants, location, state, notes, documentation, is_recurring, recurrence_frequency, recurrence_end, recurrence_occurrences) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String dStart = (e.dateStart != null) ? e.dateStart.toString() : null;
        String dEnd = (e.dateEnd != null) ? e.dateEnd.toString() : null;
        String rEnd = (e.recurrenceEnd != null) ? e.recurrenceEnd.toString() : null;

        PersistenceManager.executeUpdate(query, e.name, dStart, dEnd, e.getChefId(), e.getOrganizerId(),
                e.client, e.expectedParticipants, e.location, e.getState(), e.notes, e.documentation,
                e.recurring ? 1 : 0, e.recurrenceFrequency, rEnd, e.recurrenceOccurrences);
        e.id = PersistenceManager.getLastId();

        for (EventInstance ei : e.instances) {
            ei.setEventId(e.id);
            EventInstance.saveNewInstance(ei);
        }
    }

    /**
     * Updates an existing event record in the database.
     *
     * @param e the event to update
     */
    public static void updateEvent(Event e) {
        String query = "UPDATE Events SET name = ?, date_start = ?, date_end = ?, chef_id = ?, organizer_id = ?, client = ?, expected_participants = ?, location = ?, state = ?, notes = ?, documentation = ?, is_recurring = ?, recurrence_frequency = ?, recurrence_end = ?, recurrence_occurrences = ? WHERE id = ?";
        String dStart = (e.dateStart != null) ? e.dateStart.toString() : null;
        String dEnd = (e.dateEnd != null) ? e.dateEnd.toString() : null;
        String rEnd = (e.recurrenceEnd != null) ? e.recurrenceEnd.toString() : null;

        PersistenceManager.executeUpdate(query, e.name, dStart, dEnd, e.getChefId(), e.getOrganizerId(),
                e.client, e.expectedParticipants, e.location, e.getState(), e.notes, e.documentation,
                e.recurring ? 1 : 0, e.recurrenceFrequency, rEnd, e.recurrenceOccurrences, e.id);
    }

    /**
     * Deletes an event along with all its associated services and recurrence instances.
     *
     * @param e the event to delete
     * @return {@code true} if the event row was deleted from the database
     */
    public static boolean deleteEvent(Event e) {
        for (Service service : new ArrayList<>(e.services)) {
            Service.deleteService(service);
        }
        e.services.clear();

        for (EventInstance ei : new ArrayList<>(e.instances)) {
            EventInstance.deleteInstance(ei);
        }
        e.instances.clear();

        String query = "DELETE FROM Events WHERE id = ?";
        return PersistenceManager.executeUpdate(query, e.id) > 0;
    }

    private static Date parseSqlDate(ResultSet rs, String col) throws SQLException {
        String val = rs.getString(col);
        if (val == null || val.isEmpty()) return null;
        try {
            return Date.valueOf(val);
        } catch (IllegalArgumentException e) {
            try {
                return new Date(Long.parseLong(val));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    /**
     * Loads all events from the database ordered by start date descending.
     *
     * @return list of all persisted events
     */
    public static ArrayList<Event> loadAllEvents() {
        ArrayList<Event> events = new ArrayList<>();
        String query = "SELECT * FROM Events ORDER BY date_start DESC";
        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                events.add(mapRowToEvent(rs));
            }
        });

        for (Event e : events) {
            e.services = Service.loadServicesForEvent(e.id);
            e.instances = EventInstance.loadInstancesForEvent(e.id);
        }
        return events;
    }

    /**
     * Loads an event by its database identifier.
     *
     * @param id the event ID
     * @return the loaded {@link Event}, or {@code null} if not found
     */
    public static Event loadById(int id) {
        String query = "SELECT * FROM Events WHERE id = ?";
        return loadEventByQuery(query, id);
    }

    /**
     * Loads an event by its exact name.
     *
     * @param name the event name
     * @return the loaded {@link Event}, or {@code null} if not found
     */
    public static Event loadByName(String name) {
        String query = "SELECT * FROM Events WHERE name = ?";
        return loadEventByQuery(query, name);
    }

    private static Event mapRowToEvent(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.id = rs.getInt("id");
        e.name = rs.getString("name");
        e.dateStart = parseSqlDate(rs, "date_start");
        e.dateEnd = parseSqlDate(rs, "date_end");
        try { e.location = rs.getString("location"); } catch (SQLException ignore) {}
        try {
            State parsed = State.fromDbValue(rs.getString("state"));
            if (parsed != null) e.state = parsed;
        } catch (SQLException ignore) {}
        try { e.notes = rs.getString("notes"); } catch (SQLException ignore) {}
        try { e.documentation = rs.getString("documentation"); } catch (SQLException ignore) {}
        try { e.client = rs.getString("client"); } catch (SQLException ignore) {}
        try { e.expectedParticipants = rs.getInt("expected_participants"); } catch (SQLException ignore) {}
        try {
            e.recurring = rs.getInt("is_recurring") == 1;
            e.recurrenceFrequency = rs.getString("recurrence_frequency");
            e.recurrenceEnd = parseSqlDate(rs, "recurrence_end");
            e.recurrenceOccurrences = rs.getInt("recurrence_occurrences");
        } catch (SQLException ignore) {}
        try {
            int chefId = rs.getInt("chef_id");
            if (chefId > 0) e.chef = User.load(chefId);
        } catch (Exception ex) {
            e.chef = null;
        }
        try {
            int orgId = rs.getInt("organizer_id");
            if (orgId > 0) e.organizer = User.load(orgId);
        } catch (Exception ex) {
            e.organizer = null;
        }
        return e;
    }

    private static Event loadEventByQuery(String query, Object param) {
        final Event[] eventHolder = new Event[1];
        final boolean[] eventFound = new boolean[1];

        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                eventFound[0] = true;
                eventHolder[0] = mapRowToEvent(rs);
            }
        }, param);

        if (!eventFound[0]) return null;

        Event result = eventHolder[0];
        if (result != null) {
            try {
                result.services = Service.loadServicesForEvent(result.id);
                result.instances = EventInstance.loadInstancesForEvent(result.id);
            } catch (Exception ex) {
                result.services = new ArrayList<>();
            }
        }
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Event other = (Event) obj;
        if (this.id > 0 && other.id > 0) {
            return this.id == other.id;
        }
        return Objects.equals(this.name, other.name) && Objects.equals(this.dateStart, other.dateStart);
    }

    @Override
    public int hashCode() {
        return (id > 0) ? Integer.hashCode(id) : Objects.hash(name, dateStart);
    }

    @Override
    public String toString() {
        return "Event [id=" + id + ", name=" + name + ", location=" + location + ", state=" + getState() +
                ", services=" + (services != null ? services.size() : 0) + "]";
    }
}