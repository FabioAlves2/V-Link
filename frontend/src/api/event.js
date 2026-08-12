import api from "./axiosConfig";

export const getEvents = (filters = {}) => {
    const params = {};
    if (filters.location) params.location = filters.location;
    if (filters.date) params.date = filters.date;
    if (filters.type) params.type = filters.type;
    if (filters.keyword) params.keyword = filters.keyword;
    return api.get("/events", { params });
};

export const getEvent = (id) =>
    api.get(`/events/${id}`);

export const createEvent = (eventData) =>
    api.post("/events", eventData);

export const updateEvent = (id, eventData) =>
    api.put(`/events/${id}`, eventData);

export const deleteEvent = (id) =>
    api.delete(`/events/${id}`);

export const getMyEvents = () =>
    api.get("/events/mine");

export const getEventSubscribers = (eventId) =>
    api.get(`/events/${eventId}/subscribers`);

export const setAttendance = (eventId, userId, checkedIn) =>
    api.put(`/events/${eventId}/subscribers/${userId}/attendance`, { checkedIn });

export const uploadEventImage = (eventId, file) => {
    const formData = new FormData();
    formData.append("file", file);
    // Content-Type: undefined so axios generates the multipart boundary itself,
    // overriding the instance's default "application/json" header.
    return api.post(`/events/${eventId}/image`, formData, {
        headers: { "Content-Type": undefined },
    });
};