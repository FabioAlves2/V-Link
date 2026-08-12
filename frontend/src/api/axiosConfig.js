import axios from "axios";
import { refreshToken } from "./auth";

let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
    failedQueue.forEach((prom) =>
        error ? prom.reject(error) : prom.resolve(token)
    );
    failedQueue = [];
};

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

const api = axios.create({
    baseURL: API_BASE_URL,
    headers: { "Content-Type": "application/json" },
});

// Injeta o token em cada request
api.interceptors.request.use((config) => {
    const token = localStorage.getItem("token");
    if (token) config.headers.Authorization = `Bearer ${token}`;
    return config;
});

// Trata 401 — tenta refresh automático
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;
        const isAuthEndpoint = originalRequest.url?.startsWith("/auth/");

        if (error.response?.status !== 401 || originalRequest._retry || isAuthEndpoint) {
            return Promise.reject(error);
        }

        // Sem refreshToken guardado (ex.: visitante anónimo numa página pública que chamou um
        // endpoint autenticado) uma tentativa de refresh está destinada a falhar — e o catch
        // abaixo faz um window.location.href = "/login" duro, que rebentava com qualquer acesso
        // anónimo a uma página pública. Falha já aqui, sem tentar.
        if (!localStorage.getItem("refreshToken")) {
            return Promise.reject(error);
        }

        if (isRefreshing) {
            return new Promise((resolve, reject) => {
                failedQueue.push({ resolve, reject });
            })
                .then((token) => {
                    originalRequest.headers.Authorization = `Bearer ${token}`;
                    return api(originalRequest);
                })
                .catch((err) => Promise.reject(err));
        }

        originalRequest._retry = true;
        isRefreshing = true;

        try {
            const { data } = await refreshToken();
            const newToken = data.token;

            localStorage.setItem("token", newToken);
            localStorage.setItem("refreshToken", data.refreshToken);

            api.defaults.headers.common.Authorization = `Bearer ${newToken}`;
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            processQueue(null, newToken);

            return api(originalRequest);
        } catch (refreshError) {
            processQueue(refreshError, null);
            localStorage.removeItem("token");
            localStorage.removeItem("refreshToken");
            window.location.href = "/login";
            return Promise.reject(refreshError);
        } finally {
            isRefreshing = false;
        }
    }
);

export default api;