import api from "./axiosConfig";

export const getMe = () =>
    api.get("/auth/me");

export const updateMe = (data) =>
    api.put("/auth/me", data);

export const getSubscriptions = () =>
    api.get("/subscriptions");

export const isSubscribed = (eventId) =>
    api.get(`/subscriptions/${eventId}`);

export const subscribe = (eventId) =>
    api.post(`/subscriptions/${eventId}`);

export const unsubscribe = (eventId) =>
    api.delete(`/subscriptions/${eventId}`);