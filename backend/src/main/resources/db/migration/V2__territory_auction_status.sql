--
-- map 읽기 프로젝션 — auction-service 이벤트로만 갱신되는 read-model.
-- 영토당 활성 경매는 하나이므로 territory_id를 PK로 둔다.
--
CREATE TABLE public.territory_auction_status (
    territory_id bigint NOT NULL,
    auction_id bigint NOT NULL,
    current_price integer NOT NULL,
    end_at timestamp with time zone NOT NULL,
    CONSTRAINT territory_auction_status_pkey PRIMARY KEY (territory_id)
);

CREATE INDEX idx_territory_auction_status_auction_id
    ON public.territory_auction_status (auction_id);
