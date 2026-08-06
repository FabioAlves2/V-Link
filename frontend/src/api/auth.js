import api from "./axiosConfig";

export const login = (credentials) => api.post("/auth/login", credentials);
export const register = (userData) => api.post("/auth/register", userData);
export const refreshToken = () =>
    api.post("/auth/refresh", {
        refreshToken: localStorage.getItem("refreshToken"),
    });
export const logout = (refreshToken) => api.post("/auth/logout", { refreshToken });