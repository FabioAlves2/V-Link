import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Box, Typography, TextField, MenuItem, Card, CardMedia,
  CardContent, CardActionArea, Chip, InputAdornment, Button
} from "@mui/material";
import { Search, LocationOn, CalendarToday, FilterList } from "@mui/icons-material";
import { getEvents } from "../api/event";

const TYPE_OPTIONS = [
  { value: "", label: "Todos os tipos" },
  { value: "LIMPEZA", label: "🧹 Limpeza" },
  { value: "DOACAO", label: "🎁 Doação" },
  { value: "EDUCACAO", label: "📚 Educação" },
  { value: "AMBIENTE", label: "🌿 Ambiente" },
  { value: "SOCIAL", label: "🤝 Social" },
  { value: "OUTRO", label: "💡 Outro" },
];

const TYPE_COLORS = {
  LIMPEZA: "#3B82F6", DOACAO: "#EC4899",
  EDUCACAO: "#8B5CF6", AMBIENTE: "#10B981",
  SOCIAL: "#F59E0B", OUTRO: "#6B7280",
};

function formatDate(dt) {
  if (!dt) return "—";
  return new Date(dt).toLocaleDateString("pt-PT", {
    day: "2-digit", month: "short", year: "numeric"
  });
}

export default function EventList() {
  const navigate = useNavigate();
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [filters, setFilters] = useState({ location: "", date: "", type: "" });
  const latestRequestId = useRef(0);

  const fetchEvents = async () => {
    const requestId = ++latestRequestId.current;
    setLoading(true);
    try {
      const params = {};
      if (filters.location) params.location = filters.location;
      if (filters.date) params.date = filters.date;
      if (filters.type) params.type = filters.type;
      const { data } = await getEvents(params);
      if (requestId === latestRequestId.current) setEvents(data);
    } catch (e) {
      console.error("Erro ao buscar eventos:", e);
    } finally {
      if (requestId === latestRequestId.current) setLoading(false);
    }
  };

  // Debounce: espera que o utilizador pare de escrever antes de pesquisar
  useEffect(() => {
    const timeout = setTimeout(fetchEvents, 400);
    return () => clearTimeout(timeout);
  }, [filters]);

  const clearFilters = () => {
    setFilters({ location: "", date: "", type: "" });
  };

  const hasFilters = filters.location || filters.date || filters.type;

  return (
    <Box sx={{ maxWidth: 1200, mx: "auto", py: 4, px: { xs: 2, md: 0 } }}>

      {/* Header */}
      <Box sx={{ mb: 4 }}>
        <Typography sx={{
          fontFamily: "'Playfair Display', serif",
          fontSize: { xs: "2rem", md: "2.6rem" },
          fontWeight: 700, color: "#1A1A1A", mb: 0.5,
        }}>
          Eventos de voluntariado
        </Typography>
        <Typography sx={{ color: "#4A5568" }}>
          {events.length} evento{events.length !== 1 ? "s" : ""} disponível{events.length !== 1 ? "eis" : ""}
        </Typography>
      </Box>

      {/* Filtros */}
      <Box sx={{
        backgroundColor: "#fff", borderRadius: "16px",
        p: 3, mb: 4,
        boxShadow: "0 2px 16px rgba(27,67,50,0.07)",
        display: "flex", flexWrap: "wrap", gap: 2, alignItems: "flex-end",
      }}>
        <TextField
          label="Local" size="small" sx={{ minWidth: 180, ...fieldStyle }}
          value={filters.location}
          onChange={(e) => setFilters(f => ({ ...f, location: e.target.value }))}
          InputProps={{
            startAdornment: <InputAdornment position="start"><LocationOn sx={{ fontSize: 18, color: "#52B788" }} /></InputAdornment>
          }}
        />
        <TextField
          label="Data" size="small" type="date" sx={{ minWidth: 180, ...fieldStyle }}
          value={filters.date}
          onChange={(e) => setFilters(f => ({ ...f, date: e.target.value }))}
          InputLabelProps={{ shrink: true }}
          InputProps={{
            startAdornment: <InputAdornment position="start"><CalendarToday sx={{ fontSize: 18, color: "#52B788" }} /></InputAdornment>
          }}
        />
        <TextField
          select label="Tipo" size="small" sx={{ minWidth: 160, ...fieldStyle }}
          value={filters.type}
          onChange={(e) => setFilters(f => ({ ...f, type: e.target.value }))}
        >
          {TYPE_OPTIONS.map(o => (
            <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
          ))}
        </TextField>

        <Box sx={{ display: "flex", gap: 1, ml: "auto" }}>
          {hasFilters && (
            <Button variant="text" size="small" onClick={clearFilters}
              sx={{ color: "#4A5568" }}>
              Limpar
            </Button>
          )}
          <Button
            variant="contained" size="small" onClick={fetchEvents}
            startIcon={<Search />}
            sx={{ backgroundColor: "#1B4332", "&:hover": { backgroundColor: "#0A2318" } }}
          >
            Pesquisar
          </Button>
        </Box>
      </Box>

      {/* Grelha de eventos */}
      {loading ? (
        <Typography sx={{ color: "#4A5568", textAlign: "center", py: 8 }}>A carregar...</Typography>
      ) : events.length === 0 ? (
        <Box sx={{ textAlign: "center", py: 10 }}>
          <Typography sx={{ fontSize: "2rem", mb: 2 }}>🌱</Typography>
          <Typography sx={{ color: "#4A5568", fontSize: "1.1rem" }}>
            Nenhum evento encontrado com esses filtros.
          </Typography>
        </Box>
      ) : (
        <Box sx={{ display: "flex", flexWrap: "wrap", gap: 3 }}>
          {events.map((event) => (
            <Box key={event.id} sx={{ flex: "1 1 300px", maxWidth: { xs: "100%", sm: "calc(50% - 12px)", md: "calc(33.333% - 16px)" } }}>
              <Card
                onClick={() => navigate(`/events/${event.id}`)}
                sx={{
                  borderRadius: "16px", cursor: "pointer",
                  boxShadow: "0 2px 16px rgba(27,67,50,0.08)",
                  transition: "transform 0.2s, box-shadow 0.2s",
                  "&:hover": {
                    transform: "translateY(-4px)",
                    boxShadow: "0 12px 32px rgba(27,67,50,0.15)",
                  },
                  height: "100%", display: "flex", flexDirection: "column",
                }}
              >
                <CardActionArea sx={{ flexGrow: 1, display: "flex", flexDirection: "column", alignItems: "stretch" }}>
                  {/* Imagem */}
                  <CardMedia
                    component="img" height="180"
                    image={event.imageUrl || `https://images.unsplash.com/photo-1593113598332-cd288d649433?w=600&q=70`}
                    alt={event.title}
                    sx={{ objectFit: "cover" }}
                  />
                  <CardContent sx={{ flexGrow: 1, p: 2.5 }}>
                    {/* Tipo */}
                    {event.type && (
                      <Chip
                        label={TYPE_OPTIONS.find(t => t.value === event.type)?.label || event.type}
                        size="small"
                        sx={{
                          mb: 1.5, fontSize: "0.72rem", fontWeight: 600,
                          backgroundColor: `${TYPE_COLORS[event.type]}18`,
                          color: TYPE_COLORS[event.type],
                          border: `1px solid ${TYPE_COLORS[event.type]}30`,
                        }}
                      />
                    )}
                    {/* Título */}
                    <Typography sx={{
                      fontFamily: "'Playfair Display', serif",
                      fontWeight: 600, fontSize: "1.1rem",
                      color: "#1A1A1A", mb: 1,
                      display: "-webkit-box", WebkitLineClamp: 2,
                      WebkitBoxOrient: "vertical", overflow: "hidden",
                    }}>
                      {event.title}
                    </Typography>
                    {/* Local */}
                    <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, mb: 0.8 }}>
                      <LocationOn sx={{ fontSize: 15, color: "#52B788" }} />
                      <Typography sx={{ fontSize: "0.85rem", color: "#4A5568" }}>
                        {event.location}
                      </Typography>
                    </Box>
                    {/* Data */}
                    <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                      <CalendarToday sx={{ fontSize: 15, color: "#52B788" }} />
                      <Typography sx={{ fontSize: "0.85rem", color: "#4A5568" }}>
                        {formatDate(event.startDate)}
                      </Typography>
                    </Box>
                  </CardContent>
                </CardActionArea>
              </Card>
            </Box>
          ))}
        </Box>
      )}
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