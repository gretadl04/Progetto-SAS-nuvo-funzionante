package catering.businesslogic.event;

/**
 * Observer interface for receiving domain events emitted by {@link EventManager}.
 * Decouples business logic from persistence and UI layers.
 */
public interface EventReceiver {

    /**
     * Invoked when a new {@link Event} is created.
     *
     * @param e the newly created event
     */
    void updateEventCreated(Event e);

    /**
     * Invoked when an existing {@link Event} (and optionally its recurrence instances) is updated.
     *
     * @param e the updated event
     */
    void updateEventUpdated(Event e);

    /**
     * Invoked when an {@link Event} is permanently deleted.
     *
     * @param e the deleted event
     */
    void updateEventDeleted(Event e);

    /**
     * Invoked when a single {@link EventInstance} is updated.
     *
     * @param ei the updated event instance
     */
    void updateEventInstanceUpdated(EventInstance ei);

    /**
     * Invoked when an {@link EventInstance} is removed from a recurring event.
     *
     * @param ei the deleted event instance
     */
    void updateEventInstanceDeleted(EventInstance ei);

    /**
     * Invoked when a new {@link Service} is added to an event.
     *
     * @param e the owning event
     * @param s the newly created service
     */
    void updateServiceCreated(Event e, Service s);

    /**
     * Invoked when an existing {@link Service} (including menu approvals or amendments) is updated.
     *
     * @param s the updated service
     */
    void updateServiceUpdated(Service s);

    /**
     * Invoked when a {@link Service} is removed from an event.
     *
     * @param e the owning event
     * @param s the deleted service
     */
    void updateServiceDeleted(Event e, Service s);

    /**
     * Invoked when a new {@link StaffRequest} is added to a service.
     *
     * @param s  the owning service
     * @param sr the newly created staff request
     */
    void updateStaffRequested(Service s, StaffRequest sr);

    /**
     * Invoked when an existing {@link StaffRequest} is modified (e.g., staff assignment).
     *
     * @param s  the owning service
     * @param sr the updated staff request
     */
    void updateStaffRequestUpdated(Service s, StaffRequest sr);

    /**
     * Invoked when a {@link StaffRequest} is removed from a service.
     *
     * @param s  the owning service
     * @param sr the deleted staff request
     */
    void updateStaffRequestDeleted(Service s, StaffRequest sr);
}