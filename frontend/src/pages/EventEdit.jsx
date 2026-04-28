// Create a new Event
import { useState } from "react";
import {
  Paper, Typography, TextField, Stack, Button, Alert
} from "@mui/material";
import { useNavigate } from "react-router-dom";
import { updateEvent } from "../api/event";

export default function CreateEvent() {
  const navigate = useNavigate();
  const [form, setForm] = useState(
    updateEvent("/events/:id")
  );
  const [err, setErr] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleChange = (field) => (e) =>
    setForm((f) => ({ ...f, [field]: e.target.value }));

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErr(null);
    setLoading(true);

    // Converte "YYYY-MM-DDTHH:mm" para ISO (se preenchido)
    const toISO = (v) => (v ? new Date(v).toISOString() : null);

    try {
      await updateEvent("/events/:id", {
        title: form.title.trim(),
        location: form.location.trim(),
        description: form.description.trim(),
        startDate: toISO(form.startDate),
        endDate: toISO(form.endDate),
      });
      navigate("/");
    } catch (e) {
      setErr("Não foi possível criar o evento");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Paper component="form" onSubmit={handleSubmit} sx={{ p: 3, maxWidth: 720 }}>
      <Typography variant="h6" sx={{ mb: 2 }}>Criar novo evento</Typography>
      {err && <Alert severity="error" sx={{ mb: 2 }}>{err}</Alert>}

      <Stack spacing={2}>
        <TextField label="Título" value={form.title} onChange={handleChange("title")} required />
        <TextField label="Local" value={form.location} onChange={handleChange("location")} />
        <TextField
          label="Descrição" value={form.description} onChange={handleChange("description")}
          multiline minRows={3}
        />
        <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
          <TextField
            label="Início"
            type="datetime-local"
            value={form.startDate}
            onChange={handleChange("startDate")}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="Fim"
            type="datetime-local"
            value={form.endDate}
            onChange={handleChange("endDate")}
            InputLabelProps={{ shrink: true }}
          />
        </Stack>

        <Stack direction="row" spacing={2}>
          <Button type="submit" variant="contained" disabled={loading}>
            {loading ? "A guardar..." : "Guardar"}
          </Button>
          <Button variant="text" onClick={() => navigate("/events/:id")}>Cancelar</Button>
        </Stack>
      </Stack>
    </Paper>
  );
}
