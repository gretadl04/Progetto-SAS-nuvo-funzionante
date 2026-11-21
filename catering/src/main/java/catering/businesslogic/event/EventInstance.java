package catering.businesslogic.event;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Objects;

import catering.persistence.PersistenceManager;
import catering.persistence.ResultHandler;

/**
 * Represents a single scheduled occurrence of a recurring {@link Event}.
 * An instance can be modified or cancelled independently while remaining part of its parent recurrence.
 */
public class EventInstance {

    private int id;
    private int eventId;
    private Date date;
    private Event.State state;
    private boolean modified;
    private String customLocation;

    /**
     * Creates a default event instance in {@link Event.State#PROGRAMMATO} state.
     */
    public EventInstance() {
        this.state = Event.State.PROGRAMMATO;
        this.modified = false;
    }

    /**
     * Creates an event instance for a recurring parent event.
     *
     * @param eventId  the parent event ID
     * @param date     the scheduled date of this occurrence
     * @param location the location of this occurrence
     */
    public EventInstance(int eventId, Date date, String location) {
        this();
        this.eventId = eventId;
        this.date = date;
        this.customLocation = location;
    }

    /** @return the database identifier of this instance */
    public int getId() { return id; }

    /** @param id the database identifier to set */
    public void setId(int id) { this.id = id; }

    /** @return the parent event ID */
    public int getEventId() { return eventId; }

    /** @param eventId the parent event ID to set */
    public void setEventId(int eventId) { this.eventId = eventId; }

    /** @return the occurrence date */
    public Date getDate() { return date; }

    /** @param date the occurrence date to set */
    public void setDate(Date date) { this.date = date; }

    /** @return the typed lifecycle state of this occurrence */
    public Event.State getInstanceState() { return state; }

    /** @return the string label of the occurrence state */
    public String getState() { return state != null ? state.getDbValue() : null; }

    /** @param state the typed lifecycle state to set */
    public void setState(Event.State state) {
        if (state != null) {
            this.state = state;
        }
    }

    /** @param state the string label of the state to set */
    public void setState(String state) {
        Event.State parsed = Event.State.fromDbValue(state);
        if (parsed != null) {
            this.state = parsed;
        }
    }

    /** @return {@code true} if this occurrence was customized individually */
    public boolean isModified() { return modified; }

    /** @param modified whether this occurrence has individual overrides */
    public void setModified(boolean modified) { this.modified = modified; }

    /** @return the custom or inherited location for this occurrence */
    public String getCustomLocation() { return customLocation; }

    /** @param customLocation the location to set for this occurrence */
    public void setCustomLocation(String customLocation) { this.customLocation = customLocation; }

    // STATIC PERSISTENCE METHODS (called by EventPersistence observer)

    /**
     * Inserts a new event instance into the database.
     *
     * @param ei the event instance to persist
     */
    public static void saveNewInstance(EventInstance ei) {
        String query = "INSERT INTO EventInstances (event_id, date, state, is_modified, custom_location) VALUES (?, ?, ?, ?, ?)";
        PersistenceManager.executeUpdate(query, ei.eventId, ei.date != null ? ei.date.toString() : null,
                ei.getState(), ei.modified ? 1 : 0, ei.customLocation);
        ei.id = PersistenceManager.getLastId();
    }

    /**
     * Updates an existing event instance in the database.
     *
     * @param ei the event instance to update
     */
    public static void updateInstance(EventInstance ei) {
        String query = "UPDATE EventInstances SET date = ?, state = ?, is_modified = ?, custom_location = ? WHERE id = ?";
        PersistenceManager.executeUpdate(query, ei.date != null ? ei.date.toString() : null,
                ei.getState(), ei.modified ? 1 : 0, ei.customLocation, ei.id);
    }

    /**
     * Deletes an event instance from the database.
     *
     * @param ei the event instance to delete
     */
    public static void deleteInstance(EventInstance ei) {
        if (ei.id <= 0) return;
        String query = "DELETE FROM EventInstances WHERE id = ?";
        PersistenceManager.executeUpdate(query, ei.id);
    }

    /**
     * Loads all recurrence instances associated with a given event.
     *
     * @param eventId the parent event ID
     * @return list of persisted {@link EventInstance} objects
     */
    public static ArrayList<EventInstance> loadInstancesForEvent(int eventId) {
        ArrayList<EventInstance> list = new ArrayList<>();
        String query = "SELECT * FROM EventInstances WHERE event_id = ?";
        PersistenceManager.executeQuery(query, new ResultHandler() {
            @Override
            public void handle(ResultSet rs) throws SQLException {
                EventInstance ei = new EventInstance();
                ei.id = rs.getInt("id");
                ei.eventId = rs.getInt("event_id");
                String d = rs.getString("date");
                if (d != null && !d.isEmpty()) {
                    try { ei.date = Date.valueOf(d); } catch (IllegalArgumentException ignore) {}
                }
                Event.State parsed = Event.State.fromDbValue(rs.getString("state"));
                if (parsed != null) ei.state = parsed;
                ei.modified = rs.getInt("is_modified") == 1;
                ei.customLocation = rs.getString("custom_location");
                list.add(ei);
            }
        }, eventId);
        return list;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        EventInstance other = (EventInstance) obj;
        if (this.id > 0 && other.id > 0) {
            return this.id == other.id;
        }
        return this.eventId == other.eventId && Objects.equals(this.date, other.date);
    }

    @Override
    public int hashCode() {
        return (id > 0) ? Integer.hashCode(id) : Objects.hash(eventId, date);
    }
}