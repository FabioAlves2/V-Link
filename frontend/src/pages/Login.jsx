import { useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { Box, Button, TextField, Typography, Alert, InputAdornment, IconButton } from "@mui/material";
import { Visibility, VisibilityOff } from "@mui/icons-material";
import { useAuth } from "../context/authContext";
import { login } from "../api/auth";

export default function Login() {
    const { login: loginCtx } = useAuth();
    const navigate = useNavigate();

    const [form, setForm] = useState({ email: "", password: "" });
    const [error, setError] = useState(null);
    const [showPass, setShowPass] = useState(false);
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError(null);
        setLoading(true);
        if (!form.email || !form.password) {
            setError("Preenche todos os campos antes de continuar.");
            return;
        }
        try {
            const { data } = await login({ email: form.email, password: form.password });
            loginCtx(data.token, data.refreshToken);
            navigate("/events");
        } catch (err) {
            const msg = err.response?.data;
            setError(typeof msg === "string" ? msg : "Credenciais inválidas. Tenta novamente.");
            setLoading(false);
        }
    };

    return (
        <Box sx={{
            minHeight: "100vh", backgroundColor: "#F8F3E6",
            display: "flex", alignItems: "center", justifyContent: "center",
            px: 2,
        }}>
            {/* Card */}
            <Box sx={{
                width: "100%", maxWidth: 420,
                backgroundColor: "#fff",
                borderRadius: "20px",
                boxShadow: "0 20px 60px rgba(27,67,50,0.12)",
                p: { xs: 4, md: 5 },
            }}>
                {/* Logo */}
                <Box sx={{ textAlign: "center", mb: 4 }}>
                    <Typography
                        component={Link} to="/"
                        sx={{
                            fontFamily: "'Playfair Display', serif",
                            fontSize: "2rem", fontWeight: 700,
                            color: "#1B4332", textDecoration: "none",
                        }}
                    >
                        V-Link
                    </Typography>
                    <Typography sx={{ color: "#4A5568", mt: 1, fontSize: "0.95rem" }}>
                        Bem-vindo de volta
                    </Typography>
                </Box>

                {error && <Alert severity="error" sx={{ mb: 3, borderRadius: "10px" }}>{error}</Alert>}

                <Box component="form" onSubmit={handleSubmit} sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
                    <TextField
                        label="Email" type="email" fullWidth required
                        value={form.email}
                        onChange={(e) => setForm({ ...form, email: e.target.value })}
                        sx={fieldStyle}
                    />
                    <TextField
                        label="Password" fullWidth required
                        type={showPass ? "text" : "password"}
                        value={form.password}
                        onChange={(e) => setForm({ ...form, password: e.target.value })}
                        InputProps={{
                            endAdornment: (
                                <InputAdornment position="end">
                                    <IconButton onClick={() => setShowPass(!showPass)} edge="end">
                                        {showPass ? <VisibilityOff /> : <Visibility />}
                                    </IconButton>
                                </InputAdornment>
                            ),
                        }}
                        sx={fieldStyle}
                    />

                    <Button
                        type="submit" variant="contained" fullWidth
                        disabled={loading}
                        sx={{
                            mt: 1, py: 1.5, backgroundColor: "#1B4332",
                            fontSize: "1rem", fontWeight: 600,
                            "&:hover": { backgroundColor: "#0A2318" },
                        }}
                    >
                        {loading ? "A entrar..." : "Entrar"}
                    </Button>
                </Box>

                <Typography sx={{ textAlign: "center", mt: 3, color: "#4A5568", fontSize: "0.9rem" }}>
                    Não tens conta?{" "}
                    <Box component={Link} to="/register" sx={{ color: "#1B4332", fontWeight: 600, textDecoration: "none" }}>
                        Registar
                    </Box>
                </Typography>
            </Box>
        </Box>
    );
}

const fieldStyle = {
    "& .MuiOutlinedInput-root": {
        borderRadius: "10px",
        "&:hover fieldset": { borderColor: "#52B788" },
        "&.Mui-focused fieldset": { borderColor: "#1B4332" },
    },
    "& .MuiInputLabel-root.Mui-focused": { color: "#1B4332" },
};