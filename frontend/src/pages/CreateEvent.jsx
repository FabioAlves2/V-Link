import { useEffect, useState } from "react";
import {
  Box, Paper, Typography, TextField, Button,
  Alert, Stack, MenuItem, InputAdornment
} from "@mui/material";
import { useNavigate } from "react-router-dom";
import { createEvent } from "../api/event";

const TYPE_OPTIONS = [
  { value: "LIMPEZA", label: "🧹 Limpeza" },
  { value: "DOACAO", label: "🎁 Doação" },
  { value: "EDUCACAO", label: "📚 Educação" },
  { value: "AMBIENTE", label: "🌿 Ambiente" },
  { value: "SOCIAL", label: "🤝 Social" },
  { value: "OUTRO", label: "💡 Outro" },
];

export default function CreateEvent() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    title: "", location: "", description: "",
    startDate: "", endDate: "", imageUrl: "",
    capacity: 10, type: "OUTRO",
  });
  const [err, setErr] = useState(null);
  const [loading, setLoading] = useState(false);

  const set = (field) => (e) =>
    setForm(f => ({ ...f, [field]: e.target.value }));

  const nowISO = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
    .toISOString().slice(0, 16);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErr(null);

    const now = new Date();
    const start = form.startDate ? new Date(form.startDate) : null;
    const end = form.endDate ? new Date(form.endDate) : null;

    if (!start) {
      setErr("A data de início é obrigatória.");
      return;
    }
    if (start < now) {
      setErr("A data de início não pode ser no passado.");
      return;
    }
    if (end && end <= start) {
      setErr("A data de fim tem de ser posterior à data de início.");
      return;
    }

    setLoading(true);
    const toISO = (v) => v ? new Date(v).toISOString() : null;
    try {
      await createEvent({
        ...form,
        capacity: Number(form.capacity),
        startDate: toISO(form.startDate),
        endDate: toISO(form.endDate),
      });
      navigate("/events");
    } catch (e) {
      const msg = e.response?.data?.error;
      setErr(msg || "Não foi possível criar o evento. Verifica os campos.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Box sx={{ maxWidth: 760, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>
      <Typography sx={{
        fontFamily: "'Playfair Display', serif",
        fontSize: "2rem", fontWeight: 700,
        color: "#1A1A1A", mb: 1,
      }}>
        Criar novo evento
      </Typography>
      <Typography sx={{ color: "#4A5568", mb: 4 }}>
        Preenche os detalhes do teu evento de voluntariado.
      </Typography>

      <Box
        component="form" onSubmit={handleSubmit}
        sx={{
          backgroundColor: "#fff", borderRadius: "20px",
          p: { xs: 3, md: 4 },
          boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
        }}
      >
        {err && <Alert severity="error" sx={{ mb: 3, borderRadius: "10px" }}>{err}</Alert>}

        <Stack spacing={3}>
          {/* Título + Tipo */}
          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <Box sx={{ flex: "1 1 280px", minWidth: 0 }}>
              <TextField label="Título do evento" fullWidth required
                value={form.title} onChange={set("title")} sx={fieldStyle} />
            </Box>
            <Box sx={{ flex: "1 1 140px", minWidth: 0 }}>
              <TextField select label="Tipo" fullWidth required
                value={form.type} onChange={set("type")} sx={fieldStyle}>
                {TYPE_OPTIONS.map(o => (
                  <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
                ))}
              </TextField>
            </Box>
          </Box>

          {/* Local + Capacidade */}
          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <Box sx={{ flex: "1 1 280px", minWidth: 0 }}>
              <TextField label="Local" fullWidth required
                value={form.location} onChange={set("location")} sx={fieldStyle} />
            </Box>
            <Box sx={{ flex: "1 1 140px", minWidth: 0 }}>
              <TextField label="Capacidade" type="number" fullWidth required
                value={form.capacity} onChange={set("capacity")}
                inputProps={{ min: 1 }} sx={fieldStyle} />
            </Box>
          </Box>

          {/* Datas */}
          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <Box sx={{ flex: "1 1 200px", minWidth: 0 }}>
              <TextField
                label="Data de início" type="datetime-local" fullWidth required
                value={form.startDate}
                onChange={set("startDate")}
                inputProps={{ min: nowISO }}
                InputLabelProps={{ shrink: true }}
                sx={fieldStyle}
              />
            </Box>
            <Box sx={{ flex: "1 1 200px", minWidth: 0 }}>
              <TextField
                label="Data de fim" type="datetime-local" fullWidth
                value={form.endDate}
                onChange={set("endDate")}
                inputProps={{ min: form.startDate || nowISO }}
                InputLabelProps={{ shrink: true }}
                sx={fieldStyle}
              />
            </Box>
          </Box>

          {/* Descrição */}
          <TextField label="Descrição" fullWidth multiline minRows={4}
            value={form.description} onChange={set("description")} sx={fieldStyle} />

          {/* URL da imagem */}
          <TextField label="URL da imagem (opcional)" fullWidth
            value={form.imageUrl} onChange={set("imageUrl")}
            placeholder="https://exemplo.com/imagem.jpg"
            sx={fieldStyle} />

          {/* Preview */}
          {form.imageUrl && (
            <Box sx={{
              borderRadius: "12px", overflow: "hidden",
              height: 200, border: "1px solid #E2E8F0",
            }}>
              <img
                src={form.imageUrl} alt="Preview"
                style={{ width: "100%", height: "100%", objectFit: "cover" }}
                onError={(e) => e.target.style.display = "none"}
              />
            </Box>
          )}

          {/* Ações */}
          <Box sx={{ display: "flex", gap: 2, pt: 1 }}>
            <Button
              type="submit" variant="contained" size="large"
              disabled={loading}
              sx={{
                backgroundColor: "#1B4332", px: 4,
                "&:hover": { backgroundColor: "#0A2318" },
              }}
            >
              {loading ? "A criar..." : "Criar evento"}
            </Button>
            <Button
              variant="outlined" size="large"
              onClick={() => navigate("/events")}
              sx={{ color: "#4A5568", borderColor: "#CBD5E0" }}
            >
              Cancelar
            </Button>
          </Box>
        </Stack>
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