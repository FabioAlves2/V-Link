-- Dedup do lembrete de evento a começar em breve: uma subscrição só é elegível se ainda não
-- tiver reminder_sent_at, e é reposto a null num reagendamento (ver EventController.update).

ALTER TABLE subscriptions ADD COLUMN reminder_sent_at TIMESTAMP;
