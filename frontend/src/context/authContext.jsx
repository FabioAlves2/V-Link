import { createContext, useContext, useState } from "react";
import { logout as logoutApi } from "../api/auth";

const AuthContext = createContext();

export function AuthProvider({ children }) {
    const [token, setToken] = useState(localStorage.getItem("token"));

    const getRole = () => {
        if (!token) return null;
        try {
            const payload = JSON.parse(atob(token.split(".")[1]));
            return payload.role;
        } catch {
            return null;
        }
    };

    const login = (newToken, newRefreshToken) => {
        localStorage.setItem("token", newToken);
        localStorage.setItem("refreshToken", newRefreshToken);
        setToken(newToken);
    };

    const logout = async () => {
        const storedRefreshToken = localStorage.getItem("refreshToken");
        localStorage.removeItem("token");
        localStorage.removeItem("refreshToken");
        setToken(null);
        if (storedRefreshToken) {
            try {
                await logoutApi(storedRefreshToken);
            } catch {
                // sessão já limpa do lado do cliente mesmo que o pedido ao servidor falhe
            }
        }
    };

    return (
        <AuthContext.Provider value={{ token, login, logout, role: getRole() }}>
            {children}
        </AuthContext.Provider>
    );
}

// eslint-disable-next-line react-refresh/only-export-components -- a context+hook pair belongs in one file
export const useAuth = () => useContext(AuthContext);