import { useEffect, useState } from "react";
import {
    Box, Typography, TextField, Button, Alert,
    Avatar, Divider, Chip, CircularProgress
} from "@mui/material";
import { Person, Lock, Check } from "@mui/icons-material";
import { getMe, updateMe } from "../api/user";
import { useAuth } from "../context/authContext";

export default function Profile() {
    const { role } = useAuth();
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    const [nameForm, setNameForm] = useState({ name: "" });
    const [passForm, setPassForm] = useState({ currentPassword: "", password: "", confirm: "" });
    const [nameMsg, setNameMsg] = useState(null);
    const [passMsg, setPassMsg] = useState(null);
    const [nameSaving, setNameSaving] = useState(false);
    const [passSaving, setPassSaving] = useState(false);

    const ROLE_LABELS = { VOLUNTEER: "Voluntário", PROMOTER: "Promotor" };
    const ROLE_COLORS = { VOLUNTEER: "#52B788", PROMOTER: "#D4A853" };

    useEffect(() => {
        getMe()
            .then(({ data }) => {
                setUser(data);
                setNameForm({ name: data.name });
            })
            .finally(() => setLoading(false));
    }, []);

    const saveName = async (e) => {
        e.preventDefault();
        if (!nameForm.name.trim()) {
            setNameMsg({ type: "error", text: "O nome não pode estar vazio." });
            return;
        }
        setNameSaving(true);
        setNameMsg(null);
        try {
            const { data } = await updateMe({ name: nameForm.name });
            setUser(data);
            setNameMsg({ type: "success", text: "Nome atualizado com sucesso." });
        } catch {
            setNameMsg({ type: "error", text: "Não foi possível atualizar o nome. Tenta novamente." });
        } finally {
            setNameSaving(false);
        }
    };

    const savePassword = async (e) => {
        e.preventDefault();
        if (!passForm.currentPassword) {
            setPassMsg({ type: "error", text: "Introduz a tua password atual." });
            return;
        }
        if (passForm.password.length < 6) {
            setPassMsg({ type: "error", text: "A password deve ter pelo menos 6 caracteres." });
            return;
        }
        if (passForm.password !== passForm.confirm) {
            setPassMsg({ type: "error", text: "As passwords não coincidem." });
            return;
        }
        setPassSaving(true);
        setPassMsg(null);
        try {
            await updateMe({ currentPassword: passForm.currentPassword, password: passForm.password });
            setPassForm({ currentPassword: "", password: "", confirm: "" });
            setPassMsg({ type: "success", text: "Password alterada com sucesso." });
        } catch (err) {
            const data = err.response?.data;
            const msg = data?.errors ? Object.values(data.errors)[0] : data?.error;
            setPassMsg({ type: "error", text: msg || "Não foi possível alterar a password. Tenta novamente." });
        } finally {
            setPassSaving(false);
        }
    };

    if (loading) return (
        <Box sx={{ display: "flex", justifyContent: "center", py: 10 }}>
            <CircularProgress sx={{ color: "#1B4332" }} />
        </Box>
    );

    return (
        <Box sx={{ maxWidth: 640, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>

            {/* Header */}
            <Box sx={{
                backgroundColor: "#1B4332", borderRadius: "20px",
                p: { xs: 3, md: 4 }, mb: 3,
                display: "flex", alignItems: "center", gap: 3,
            }}>
                <Avatar sx={{
                    width: 72, height: 72,
                    backgroundColor: "#52B788",
                    fontSize: "1.8rem", fontWeight: 700,
                    color: "#1B4332",
                }}>
                    {user?.name?.[0]?.toUpperCase()}
                </Avatar>
                <Box>
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontSize: "1.6rem", fontWeight: 700,
                        color: "#F8F3E6",
                    }}>
                        {user?.name}
                    </Typography>
                    <Typography sx={{ color: "#A8D5B5", mb: 1, fontSize: "0.9rem" }}>
                        {user?.email}
                    </Typography>
                    <Chip
                        label={ROLE_LABELS[role] || role}
                        size="small"
                        sx={{
                            backgroundColor: `${ROLE_COLORS[role]}22`,
                            color: ROLE_COLORS[role],
                            fontWeight: 600, fontSize: "0.75rem",
                            border: `1px solid ${ROLE_COLORS[role]}44`,
                        }}
                    />
                </Box>
            </Box>

            {/* Editar nome */}
            <Box sx={{
                backgroundColor: "#fff", borderRadius: "20px",
                p: { xs: 3, md: 4 }, mb: 3,
                boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
            }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 3 }}>
                    <Box sx={{
                        width: 36, height: 36, borderRadius: "10px",
                        backgroundColor: "#1B433312",
                        display: "flex", alignItems: "center", justifyContent: "center",
                    }}>
                        <Person sx={{ color: "#1B4332", fontSize: 18 }} />
                    </Box>
                    <Typography sx={{ fontWeight: 600, fontSize: "1.05rem", color: "#1A1A1A" }}>
                        Informação pessoal
                    </Typography>
                </Box>

                {nameMsg && (
                    <Alert severity={nameMsg.type} sx={{ mb: 2, borderRadius: "10px" }}>
                        {nameMsg.text}
                    </Alert>
                )}

                <Box component="form" onSubmit={saveName} sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                    <TextField
                        label="Nome completo" fullWidth
                        value={nameForm.name}
                        onChange={(e) => setNameForm({ name: e.target.value })}
                        sx={fieldStyle}
                    />
                    <TextField
                        label="Email" fullWidth disabled
                        value={user?.email || ""}
                        helperText="O email não pode ser alterado."
                        sx={fieldStyle}
                    />
                    <Button
                        type="submit" variant="contained" disabled={nameSaving}
                        startIcon={nameSaving ? <CircularProgress size={16} color="inherit" /> : <Check />}
                        sx={{
                            alignSelf: "flex-start", backgroundColor: "#1B4332",
                            "&:hover": { backgroundColor: "#0A2318" },
                        }}
                    >
                        {nameSaving ? "A guardar..." : "Guardar alterações"}
                    </Button>
                </Box>
            </Box>

            {/* Alterar password */}
            <Box sx={{
                backgroundColor: "#fff", borderRadius: "20px",
                p: { xs: 3, md: 4 },
                boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
            }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1.5, mb: 3 }}>
                    <Box sx={{
                        width: 36, height: 36, borderRadius: "10px",
                        backgroundColor: "#1B433312",
                        display: "flex", alignItems: "center", justifyContent: "center",
                    }}>
                        <Lock sx={{ color: "#1B4332", fontSize: 18 }} />
                    </Box>
                    <Typography sx={{ fontWeight: 600, fontSize: "1.05rem", color: "#1A1A1A" }}>
                        Alterar password
                    </Typography>
                </Box>

                {passMsg && (
                    <Alert severity={passMsg.type} sx={{ mb: 2, borderRadius: "10px" }}>
                        {passMsg.text}
                    </Alert>
                )}

                <Box component="form" onSubmit={savePassword} sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
                    <TextField
                        label="Password atual" type="password" fullWidth
                        value={passForm.currentPassword}
                        onChange={(e) => setPassForm(f => ({ ...f, currentPassword: e.target.value }))}
                        sx={fieldStyle}
                    />
                    <TextField
                        label="Nova password" type="password" fullWidth
                        value={passForm.password}
                        onChange={(e) => setPassForm(f => ({ ...f, password: e.target.value }))}
                        sx={fieldStyle}
                    />
                    <TextField
                        label="Confirmar password" type="password" fullWidth
                        value={passForm.confirm}
                        onChange={(e) => setPassForm(f => ({ ...f, confirm: e.target.value }))}
                        sx={fieldStyle}
                    />
                    <Button
                        type="submit" variant="contained" disabled={passSaving}
                        startIcon={passSaving ? <CircularProgress size={16} color="inherit" /> : <Lock />}
                        sx={{
                            alignSelf: "flex-start", backgroundColor: "#1B4332",
                            "&:hover": { backgroundColor: "#0A2318" },
                        }}
                    >
                        {passSaving ? "A alterar..." : "Alterar password"}
                    </Button>
                </Box>
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