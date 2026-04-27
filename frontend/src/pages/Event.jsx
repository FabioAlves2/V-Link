// Specific info about event
import { useEffect, useMemo, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import {
  Alert, Button, Chip, Divider, Paper, Skeleton, Stack, Typography, Snackbar
} from "@mui/material";
import { api } from "../api";

export default function Event() {
    const { id } = useParams();           // <- vem da rota /events/:id
    const navigate = useNavigate();
    const [event, setEvent] = useState(null);
    const [loading, setLoading] = useState(true);
    const [err, setErr] = useState(null);
    const [joining, setJoining] = useState(false);
    const [snack, setSnack] = useState("");

    // fetch quando o id muda
    useEffect(() => {
    let cancel = false;
    setLoading(true);
    setErr(null);

    api.get(`/events/${id}`)
        .then(res => { if (!cancel) setEvent(res.data); })
        .catch(() => { if (!cancel) setErr("Não foi possível carregar o evento."); })
        .finally(() => { if (!cancel) setLoading(false); });

    return () => { cancel = true; };
    }, [id]);

    const startLabel = useMemo(() =>
    event?.startDate ? new Date(event.startDate).toLocaleString() : null, [event]);
    const endLabel = useMemo(() =>
    event?.endDate ? new Date(event.endDate).toLocaleString() : null, [event]);

    const isParticipant = !!event?.isParticipant; // adapta ao que o backend devolve

    async function handleJoin() {
    setJoining(true);
    setErr(null);

    // optimistic UI
    const prev = event;
    setEvent(prev ? { ...prev, isParticipant: true } : prev);

    try {
        await api.post(`/events/${id}/participants`); // adapta ao teu endpoint
        setSnack("Inscrição confirmada!");
    } catch {
        // reverte se falhar
        setEvent(prev);
        setErr("Falha ao inscrever. Tenta novamente.");
    } finally {
        setJoining(false);
    }
    }

    async function onDelete(id){
        await api.delete(`/events/${id}`);
        setEvents(events.filter(ev => ev.id !== id));
    }


    // UI
    if (loading) {
    return (
        <Paper sx={{ p: 3 }}>
        <Skeleton variant="text" width={240} height={36} />
        <Skeleton variant="text" width={160} />
        <Divider sx={{ my: 2 }} />
        <Skeleton variant="rounded" height={120} />
        </Paper>
    );
    }

    if (err) return <Alert severity="error" action={
    <Button color="inherit" size="small" onClick={() => navigate(0)}>Tentar</Button>
    }>{err}</Alert>;

    if (!event) return <Alert severity="info">Evento não encontrado.</Alert>;

    return (
    <>
        <Paper sx={{ p: 3 }}>
        <Stack direction="row" justifyContent="space-between" alignItems="start" gap={2}>
            <div>
            <Typography variant="h5" gutterBottom>{event.title}</Typography>
            <Typography variant="body2" color="text.secondary">{event.location}</Typography>
            <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: "wrap" }}>
                {startLabel && <Chip label={`Início: ${startLabel}`} />}
                {endLabel && <Chip label={`Fim: ${endLabel}`} />}
            </Stack>
            </div>

            <Stack direction="row" spacing={1}>
            <Button variant="text" onClick={() => navigate(-1)}>Voltar</Button>
            <Button
                variant="contained"
                onClick={handleJoin}
                disabled={joining || isParticipant}
            >
                {isParticipant ? "Inscrito" : (joining ? "A inscrever..." : "Participar")}
            </Button>
            </Stack>
        </Stack>

        <Divider sx={{ my: 2 }} />
        <Typography sx={{ whiteSpace: "pre-wrap" }}>
            {event.description || "Sem descrição."}
        </Typography>

        <Stack direction="row" justifyContent="space-between" alignItems="start" gap={2}>
            <button onClick={()=>onDelete(id)}>Eliminar</button>
            <button onClick={()=>navigate(`/events/${e.id}/edit`)}>Editar</button>
        </Stack>
        </Paper>

        <Snackbar
        open={!!snack}
        autoHideDuration={2500}
        onClose={() => setSnack("")}
        message={snack}
        />
    </>
    );
}
