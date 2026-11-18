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

public class Event {

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
    private String state;
    private String notes;
    private String documentation;

    private boolean recurring;
    private String recurrenceFrequency;
    private Date recurrenceEnd;
    private int recurrenceOccurrences;
    private ArrayList<EventInstance> instances;

    public Event() {
        services = new ArrayList<>();
        instances = new ArrayList<>();
        this.state = "in lavorazione";
        this.recurring = false;
    }

    public Event(String name) {
        this();
        this.name = name;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Date getDateStart() { return dateStart; }
    public void setDateStart(Date dateStart) { this.dateStart = dateStart; }
    public Date getDateEnd() { return dateEnd; }
    public void setDateEnd(Date dateEnd) { this.dateEnd = dateEnd; }
    public User getChef() { return chef; }
    public int getChefId() { return chef != null ? chef.getId() : 0; }
    public void setChef(User chef) { this.chef = chef; }
    public void setChefId(int chefId) { this.chef = User.load(chefId); }

    public User getOrganizer() { return organizer; }
    public int getOrganizerId() { return organizer != null ? organizer.getId() : 0; }
    public void setOrganizer(User organizer) { this.organizer = organizer; }
    public void setOrganizerId(int organizerId) { this.organizer = User.load(organizerId); }

    public String getClient() { return client; }
    public void setClient(String client) { this.client = client; }
    public int getExpectedParticipants() { return expectedParticipants; }
    public void setExpectedParticipants(int expectedParticipants) { this.expectedParticipants = expectedParticipants; }

    public ArrayList<Service> getServices() { return services; }
    public void setServices(ArrayList<Service> services) { this.services = services; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getDocumentation() { return documentation; }
    public void setDocumentation(String documentation) { this.documentation = documentation; }

    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }
    public String getRecurrenceFrequency() { return recurrenceFrequency; }
    public void setRecurrenceFrequency(String recurrenceFrequency) { this.recurrenceFrequency = recurrenceFrequency; }
    public Date getRecurrenceEnd() { return recurrenceEnd; }
    public void setRecurrenceEnd(Date recurrenceEnd) { this.recurrenceEnd = recurrenceEnd; }
    public int getRecurrenceOccurrences() { return recurrenceOccurrences; }
    public void setRecurrenceOccurrences(int recurrenceOccurrences) { this.recurrenceOccurrences = recurrenceOccurrences; }
    public ArrayList<EventInstance> getInstances() { return instances; }

    public void addService(Service service) {
        if (services == null) services = new ArrayList<>();
        if (!services.contains(service)) {
            services.add(service);
        }
    }

    public void removeService(Service service) {
        if (services != null) services.remove(service);
    }

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

    // Generazione istanze di ricorrenza con data di fine
    public void generateRecurrenceInstances(String frequency, Date endRecurrence) {
        updateRecurrenceInstances(frequency, endRecurrence, null);
    }

    // Generazione istanze di ricorrenza con numero di ripetizioni
    public void generateRecurrenceInstances(String frequency, int occurrences) {
        updateRecurrenceInstances(frequency, null, occurrences);
    }

    // Gestione creazione e modifica della ricorrenza preservando le istanze già in calendario
    public void updateRecurrenceInstances(String frequency, Date endRecurrence, Integer occurrences) {
        this.recurring = true;
        this.recurrenceFrequency = frequency;
        this.recurrenceEnd = endRecurrence;
        this.recurrenceOccurrences = (occurrences != null) ? occurrences : 0;

        if (this.dateStart == null) return;
        if (endRecurrence == null && (occurrences == null || occurrences <= 0)) return;

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

        // Conserva le istanze già esistenti in calendario prima della modifica
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

        // Elimina dal DB eventuali istanze di troppo
        for (int i = targetDates.size(); i < this.instances.size(); i++) {
            EventInstance excess = this.instances.get(i);
            if (excess.getId() > 0) {
                excess.deleteInstance();
            }
        }

        this.instances = updatedInstances;
    }

    // Operazioni Database
    public void saveNewEvent() {
        String query = "INSERT INTO Events (name, date_start, date_end, chef_id, organizer_id, client, expected_participants, location, state, notes, documentation, is_recurring, recurrence_frequency, recurrence_end, recurrence_occurrences) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String dStart = (dateStart != null) ? dateStart.toString() : null;
        String dEnd = (dateEnd != null) ? dateEnd.toString() : null;
        String rEnd = (recurrenceEnd != null) ? recurrenceEnd.toString() : null;

        PersistenceManager.executeUpdate(query, name, dStart, dEnd, getChefId(), getOrganizerId(), client, expectedParticipants, location, state, notes, documentation, recurring ? 1 : 0, recurrenceFrequency, rEnd, recurrenceOccurrences);
        id = PersistenceManager.getLastId();

        for (EventInstance ei : instances) {
            ei.setEventId(this.id);
            ei.saveNewInstance();
        }
    }

    public void updateEvent() {
        String query = "UPDATE Events SET name = ?, date_start = ?, date_end = ?, chef_id = ?, organizer_id = ?, client = ?, expected_participants = ?, location = ?, state = ?, notes = ?, documentation = ?, is_recurring = ?, recurrence_frequency = ?, recurrence_end = ?, recurrence_occurrences = ? WHERE id = ?";
        String dStart = (dateStart != null) ? dateStart.toString() : null;
        String dEnd = (dateEnd != null) ? dateEnd.toString() : null;
        String rEnd = (recurrenceEnd != null) ? recurrenceEnd.toString() : null;

        PersistenceManager.executeUpdate(query, name, dStart, dEnd, getChefId(), getOrganizerId(), client, expectedParticipants, location, state, notes, documentation, recurring ? 1 : 0, recurrenceFrequency, rEnd, recurrenceOccurrences, id);
    }

    public boolean deleteEvent() {
        for (Service service : services) {
            service.deleteService();
        }
        services.clear();

        for (EventInstance ei : instances) {
            ei.deleteInstance();
        }
        instances.clear();

        String query = "DELETE FROM Events WHERE id = ?";
        return PersistenceManager.executeUpdate(query, id) > 0;
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

    public static ArrayList<Event> loadAllEvents() {
        ArrayList<Event> events = new ArrayList<>();
        String query = "SELECT * FROM Events ORDER BY date_start DESC";
        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                Event e = mapRowToEvent(rs);
                events.add(e);
            }
        });

        for (Event e : events) {
            e.services = Service.loadServicesForEvent(e.id);
            e.instances = EventInstance.loadInstancesForEvent(e.id);
        }
        return events;
    }

    public static Event loadById(int id) {
        String query = "SELECT * FROM Events WHERE id = ?";
        return loadEventByQuery(query, id);
    }

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
        try { e.state = rs.getString("state"); } catch (SQLException ignore) {}
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
        return "Event [id=" + id + ", name=" + name + ", location=" + location + ", state=" + state +
                ", services=" + (services != null ? services.size() : 0) + "]";
    }
}