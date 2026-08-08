import { useEffect, useState } from "react";
import { useParams, Link as RouterLink } from "react-router-dom";
import {
    Box, Typography, Card, CardContent, Checkbox, Button,
    CircularProgress, Alert
} from "@mui/material";
import { Download, ArrowBack } from "@mui/icons-material";
import { getEvent, getEventSubscribers, setAttendance } from "../api/event";
import { toCsv, downloadCsv } from "../utils/csv";

const CSV_HEADERS = [
    { key: "name", label: "Nome" },
    { key: "email", label: "Email" },
    { key: "checkedIn", label: "Presente" },
];

export default function EventSubscribers() {
    const { id } = useParams();
    const [event, setEvent] = useState(null);
    const [subscribers, setSubscribers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [forbidden, setForbidden] = useState(false);
    const [toggling, setToggling] = useState(null);

    useEffect(() => {
        let cancelled = false;

        Promise.all([getEvent(id), getEventSubscribers(id)])
            .then(([eventRes, subsRes]) => {
                if (cancelled) return;
                setEvent(eventRes.data);
                setSubscribers(subsRes.data);
            })
            .catch((err) => {
                if (cancelled) return;
                if (err.response?.status === 403) setForbidden(true);
                else setError("Erro ao carregar os inscritos.");
            })
            .finally(() => { if (!cancelled) setLoading(false); });

        return () => { cancelled = true; };
    }, [id]);

    const handleToggle = async (subscriber) => {
        setToggling(subscriber.userId);
        try {
            const { data } = await setAttendance(id, subscriber.userId, !subscriber.checkedIn);
            setSubscribers((prev) => prev.map((s) => (s.userId === data.userId ? data : s)));
        } catch {
            setError("Erro ao atualizar presença.");
        } finally {
            setToggling(null);
        }
    };

    const handleExport = () => {
        const rows = subscribers.map((s) => ({ ...s, checkedIn: s.checkedIn ? "Sim" : "Não" }));
        downloadCsv(`inscritos-evento-${id}.csv`, toCsv(rows, CSV_HEADERS));
    };

    if (loading) {
        return (
            <Box sx={{ display: "flex", justifyContent: "center", py: 10 }}>
                <CircularProgress sx={{ color: "#1B4332" }} />
            </Box>
        );
    }

    if (forbidden) {
        return (
            <Box sx={{ maxWidth: 700, mx: "auto", py: 6, px: { xs: 2, md: 0 } }}>
                <Alert severity="error" sx={{ mb: 3, borderRadius: "10px" }}>
                    Só o promotor que criou este evento pode ver os seus inscritos.
                </Alert>
                <Button component={RouterLink} to="/dashboard" startIcon={<ArrowBack />}>
                    Voltar ao painel
                </Button>
            </Box>
        );
    }

    return (
        <Box sx={{ maxWidth: 900, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>
            <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", mb: 4, flexWrap: "wrap", gap: 2 }}>
                <Box>
                    <Typography sx={{
                        fontFamily: "'Playfair Display', serif",
                        fontSize: { xs: "1.7rem", md: "2rem" },
                        fontWeight: 700, color: "#1A1A1A", mb: 0.5,
                    }}>
                        Inscritos — {event?.title}
                    </Typography>
                    <Typography sx={{ color: "#4A5568" }}>
                        {subscribers.length} inscrito{subscribers.length !== 1 ? "s" : ""}
                    </Typography>
                </Box>
                <Button
                    variant="outlined" startIcon={<Download />}
                    onClick={handleExport}
                    disabled={subscribers.length === 0}
                    sx={{ color: "#1B4332", borderColor: "#1B433240" }}
                >
                    Exportar CSV
                </Button>
            </Box>

            {error && (
                <Alert severity="error" sx={{ mb: 3, borderRadius: "10px" }}>{error}</Alert>
            )}

            {subscribers.length === 0 ? (
                <Box sx={{
                    textAlign: "center", py: 8,
                    backgroundColor: "#fff", borderRadius: "20px",
                    boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
                }}>
                    <Typography sx={{ fontSize: "2.5rem", mb: 2 }}>🤷</Typography>
                    <Typography sx={{ color: "#4A5568" }}>Ainda ninguém se inscreveu neste evento.</Typography>
                </Box>
            ) : (
                <Box sx={{ display: "flex", flexDirection: "column", gap: 1.5 }}>
                    {subscribers.map((s) => (
                        <Card key={s.userId} sx={{
                            borderRadius: "14px",
                            boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
                        }}>
                            <CardContent sx={{ display: "flex", alignItems: "center", gap: 2, py: 1.5 }}>
                                <Checkbox
                                    checked={s.checkedIn}
                                    disabled={toggling === s.userId}
                                    onChange={() => handleToggle(s)}
                                    sx={{ color: "#1B4332", "&.Mui-checked": { color: "#16A34A" } }}
                                />
                                <Box sx={{ flex: 1, minWidth: 0 }}>
                                    <Typography sx={{ fontWeight: 600, color: "#1A1A1A" }}>{s.name}</Typography>
                                    <Typography sx={{ fontSize: "0.85rem", color: "#4A5568" }}>{s.email}</Typography>
                                </Box>
                                <Typography sx={{ fontSize: "0.8rem", color: s.checkedIn ? "#16A34A" : "#4A5568" }}>
                                    {s.checkedIn ? "Presente" : "Não confirmado"}
                                </Typography>
                            </CardContent>
                        </Card>
                    ))}
                </Box>
            )}
        </Box>
    );
}
