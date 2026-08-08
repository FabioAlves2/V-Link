import { useEffect, useState } from "react";
import {
  Box, Typography, TextField, Button,
  Alert, Stack, MenuItem
} from "@mui/material";
import { useNavigate, useParams } from "react-router-dom";
import { getEvent, updateEvent, uploadEventImage } from "../api/event";
import { resolveImageUrl } from "../utils/image";

const TYPE_OPTIONS = [
  { value: "LIMPEZA", label: "🧹 Limpeza" },
  { value: "DOACAO", label: "🎁 Doação" },
  { value: "EDUCACAO", label: "📚 Educação" },
  { value: "AMBIENTE", label: "🌿 Ambiente" },
  { value: "SOCIAL", label: "🤝 Social" },
  { value: "OUTRO", label: "💡 Outro" },
];

const STATUS_OPTIONS = [
  { value: "DRAFT", label: "Rascunho" },
  { value: "PUBLISHED", label: "Publicado" },
  { value: "CLOSED", label: "Encerrado" },
];

// Espelha a máquina de estados do backend (EventController.update): publicado com inscritos não
// pode voltar a rascunho (usa Cancelar/Encerrar no painel) e encerrado é permanente — sem isto o
// dropdown oferece transições que o servidor vai rejeitar com um 400 confuso. Sem inscritos,
// despublicar continua permitido (nada a proteger ainda).
function allowedStatusOptionsFrom(currentStatus, subscriberCount) {
  if (currentStatus === "PUBLISHED" && subscriberCount > 0) return STATUS_OPTIONS.filter(o => o.value !== "DRAFT");
  if (currentStatus === "CLOSED") return STATUS_OPTIONS.filter(o => o.value === "CLOSED");
  return STATUS_OPTIONS;
}

// Converte um ISO datetime do backend para o formato aceite por <input type="datetime-local">
function toLocalInput(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  return new Date(d - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

export default function EventEdit() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [form, setForm] = useState(null);
  // Estado/inscritos originais tal como vieram do servidor — as opções legais do dropdown
  // dependem de onde o evento ESTÁ agora, não do que o utilizador já tenha selecionado.
  const [originalStatus, setOriginalStatus] = useState(null);
  const [originalSubscriberCount, setOriginalSubscriberCount] = useState(0);
  const [fetching, setFetching] = useState(true);
  const [fetchError, setFetchError] = useState(null);
  const [err, setErr] = useState(null);
  const [loading, setLoading] = useState(false);
  const [uploadingImage, setUploadingImage] = useState(false);

  useEffect(() => {
    getEvent(id)
      .then(({ data }) => {
        setOriginalStatus(data.status);
        setOriginalSubscriberCount(data.subscriberCount ?? 0);
        setForm({
          title: data.title,
          location: data.location,
          description: data.description || "",
          startDate: toLocalInput(data.startDate),
          endDate: toLocalInput(data.endDate),
          imageUrl: data.imageUrl || "",
          capacity: data.capacity,
          type: data.type,
          status: data.status,
        });
      })
      .catch(() => setFetchError("Não foi possível carregar este evento."))
      .finally(() => setFetching(false));
  }, [id]);

  const set = (field) => (e) =>
    setForm(f => ({ ...f, [field]: e.target.value }));

  const handleFileChange = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploadingImage(true);
    setErr(null);
    try {
      const { data } = await uploadEventImage(id, file);
      setForm(f => ({ ...f, imageUrl: data.imageUrl }));
    } catch (e) {
      setErr(e.response?.data?.error || "Não foi possível enviar a imagem.");
    } finally {
      setUploadingImage(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErr(null);

    const start = form.startDate ? new Date(form.startDate) : null;
    const end = form.endDate ? new Date(form.endDate) : null;

    if (!start) {
      setErr("A data de início é obrigatória.");
      return;
    }
    if (end && end <= start) {
      setErr("A data de fim tem de ser posterior à data de início.");
      return;
    }

    setLoading(true);
    // O backend guarda startDate/endDate como LocalDateTime (sem timezone) e trata-os
    // como hora local do servidor — NÃO usar .toISOString() aqui, que converte para UTC
    // e desalinha a hora (ex.: 15:30 local passa a ser lido como 14:30, "no passado").
    const toISO = (v) => v ? `${v}:00` : null;
    try {
      await updateEvent(id, {
        ...form,
        capacity: Number(form.capacity),
        startDate: toISO(form.startDate),
        endDate: toISO(form.endDate),
      });
      navigate(`/events/${id}`);
    } catch (e) {
      const errors = e.response?.data?.errors;
      const msg = errors ? Object.values(errors)[0] : e.response?.data?.error;
      setErr(msg || "Não foi possível guardar as alterações.");
    } finally {
      setLoading(false);
    }
  };

  if (fetching) return (
    <Box sx={{ py: 8, textAlign: "center" }}>
      <Typography sx={{ color: "#4A5568" }}>A carregar...</Typography>
    </Box>
  );

  if (fetchError || !form) return (
    <Box sx={{ maxWidth: 760, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>
      <Alert severity="error">{fetchError || "Evento não encontrado."}</Alert>
    </Box>
  );

  return (
    <Box sx={{ maxWidth: 760, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>
      <Typography sx={{
        fontFamily: "'Playfair Display', serif",
        fontSize: "2rem", fontWeight: 700,
        color: "#1A1A1A", mb: 1,
      }}>
        Editar evento
      </Typography>
      <Typography sx={{ color: "#4A5568", mb: 4 }}>
        Atualiza os detalhes do teu evento de voluntariado.
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
            <Box sx={{ flex: "1 1 140px", minWidth: 0 }}>
              <TextField select label="Estado" fullWidth required
                disabled={originalStatus === "CLOSED"}
                value={form.status} onChange={set("status")} sx={fieldStyle}>
                {allowedStatusOptionsFrom(originalStatus, originalSubscriberCount).map(o => (
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
                InputLabelProps={{ shrink: true }}
                sx={fieldStyle}
              />
            </Box>
            <Box sx={{ flex: "1 1 200px", minWidth: 0 }}>
              <TextField
                label="Data de fim" type="datetime-local" fullWidth
                value={form.endDate}
                onChange={set("endDate")}
                InputLabelProps={{ shrink: true }}
                sx={fieldStyle}
              />
            </Box>
          </Box>

          {/* Descrição */}
          <TextField label="Descrição" fullWidth multiline minRows={4}
            value={form.description} onChange={set("description")} sx={fieldStyle} />

          {/* Imagem */}
          <Button component="label" variant="outlined" disabled={uploadingImage}
            sx={{ color: "#1B4332", borderColor: "#1B4332", alignSelf: "flex-start" }}>
            {uploadingImage ? "A enviar..." : form.imageUrl ? "Trocar imagem" : "Escolher imagem (opcional)"}
            <input type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={handleFileChange} />
          </Button>

          {/* Preview */}
          {form.imageUrl && (
            <Box sx={{
              borderRadius: "12px", overflow: "hidden",
              height: 200, border: "1px solid #E2E8F0",
            }}>
              <img
                src={resolveImageUrl(form.imageUrl)} alt="Preview"
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
              {loading ? "A guardar..." : "Guardar alterações"}
            </Button>
            <Button
              variant="outlined" size="large"
              onClick={() => navigate(`/events/${id}`)}
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
