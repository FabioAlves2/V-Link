//List all the events
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { Card, CardActionArea, CardContent } from "@mui/material";
import { api } from "../api";

export default function EventList() {
  const [events, setEvents] = useState([]);

  useEffect(() => {
    api.get("/events")
      .then((res) => setEvents(res.data))
      .catch((err) => console.error("Erro ao buscar eventos:", err));
  }, []);

  return (
    <div style={{ padding: 24 }}>
      <h2>Eventos</h2>
      {events.length === 0 ? (
        <p>Nenhum evento encontrado</p>
      ) : (
        events.map((e) => (
          <Card key={e.id} variant="outlined" sx={{ mb: 2 }}>
            <CardActionArea component={Link} to={`/events/${e.id}`}>
              <CardContent>
                <strong>{e.title}</strong> — {e.location}
                <div>{e.description}</div>
                {e.startDate && e.endDate && (
                  <small>
                    {new Date(e.startDate).toLocaleString()} → {new Date(e.endDate).toLocaleString()}
                  </small>
                )}
              </CardContent>
            </CardActionArea>
          </Card>
        ))
      )}
    </div>
  );
}