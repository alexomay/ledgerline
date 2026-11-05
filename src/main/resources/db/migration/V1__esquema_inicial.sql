-- Usuarios y cuentas ---------------------------------------------------------

create table users (
    id            uuid primary key,
    email         varchar(254) not null unique,
    password_hash varchar(100) not null,
    full_name     varchar(120) not null,
    role          varchar(20)  not null,
    created_at    timestamptz  not null default now()
);

-- Las cuentas de clientes tienen dueño y CLABE. Las cuentas de sistema (por
-- ejemplo, la cuenta puente de SPEI) tienen un código y pueden quedar en negativo.
create table accounts (
    id             uuid primary key,
    user_id        uuid references users (id),
    code           varchar(40) unique,
    clabe          varchar(18) unique,
    kind           varchar(20) not null,
    currency       varchar(3)  not null default 'MXN',
    balance        bigint      not null default 0,
    allow_negative boolean     not null default false,
    created_at     timestamptz not null default now(),
    constraint accounts_balance_non_negative check (allow_negative or balance >= 0),
    constraint accounts_customer_has_owner check (kind <> 'CUSTOMER' or (user_id is not null and clabe is not null)),
    constraint accounts_clabe_format check (clabe is null or clabe ~ '^[0-9]{18}$')
);

create unique index accounts_one_customer_account_per_user on accounts (user_id) where kind = 'CUSTOMER';

-- Libro contable ---------------------------------------------------------------

create table journal_entries (
    id          uuid primary key,
    kind        varchar(20)  not null,
    description varchar(200) not null,
    created_at  timestamptz  not null default now()
);

-- Montos en centavos. Positivo = abono a la cuenta, negativo = cargo.
create table postings (
    id            bigint generated always as identity primary key,
    entry_id      uuid        not null references journal_entries (id),
    account_id    uuid        not null references accounts (id),
    amount        bigint      not null check (amount <> 0),
    balance_after bigint      not null,
    created_at    timestamptz not null default now()
);

create index postings_by_account on postings (account_id, id desc);
create index postings_by_entry on postings (entry_id);

-- Regla de la doble partida: al hacer commit, cada asiento debe sumar cero y
-- tener al menos dos movimientos. Se revisa al final de la transacción
-- (deferrable) para permitir insertar los movimientos uno por uno.
create function check_entry_balanced() returns trigger
    language plpgsql as
$$
declare
    total bigint;
    legs  int;
begin
    select coalesce(sum(amount), 0), count(*)
    into total, legs
    from postings
    where entry_id = new.entry_id;

    if total <> 0 or legs < 2 then
        raise exception 'asiento % descuadrado: suma %, movimientos %', new.entry_id, total, legs
            using errcode = '23514';
    end if;
    return null;
end;
$$;

create constraint trigger postings_entry_balanced
    after insert on postings
    deferrable initially deferred
    for each row
execute function check_entry_balanced();

-- El libro es de solo inserción: un movimiento nunca se edita ni se borra; un
-- error se corrige con un asiento inverso.
create function reject_posting_changes() returns trigger
    language plpgsql as
$$
begin
    raise exception 'los movimientos contables no se modifican ni se borran'
        using errcode = '42501';
end;
$$;

create trigger postings_append_only
    before update or delete on postings
    for each row
execute function reject_posting_changes();

-- Operaciones ------------------------------------------------------------------

create table transfers (
    id              uuid primary key,
    entry_id        uuid         not null unique references journal_entries (id),
    from_account_id uuid         not null references accounts (id),
    to_account_id   uuid         not null references accounts (id),
    amount          bigint       not null check (amount > 0),
    concept         varchar(140) not null,
    created_by      uuid         not null references users (id),
    created_at      timestamptz  not null default now(),
    constraint transfers_distinct_accounts check (from_account_id <> to_account_id)
);

create index transfers_by_source_and_time on transfers (from_account_id, created_at);

-- Depósitos SPEI recibidos. La clave de rastreo es única: si el banco reenvía
-- la misma notificación, no se abona dos veces.
create table spei_deposits (
    id           uuid primary key,
    tracking_key varchar(30)  not null unique,
    account_id   uuid references accounts (id),
    entry_id     uuid unique references journal_entries (id),
    amount       bigint       not null check (amount > 0),
    sender_name  varchar(120) not null,
    sender_bank  varchar(60)  not null,
    received_at  timestamptz  not null default now()
);

-- Llaves de idempotencia: un reintento con la misma llave devuelve la misma
-- transferencia en lugar de crear otra.
create table idempotency_keys (
    user_id      uuid        not null references users (id),
    idem_key     varchar(80) not null,
    request_hash varchar(64) not null,
    transfer_id  uuid references transfers (id),
    created_at   timestamptz not null default now(),
    primary key (user_id, idem_key)
);

-- Bandeja de salida (transactional outbox): los eventos se guardan en la misma
-- transacción que la operación y un proceso aparte los publica.
create table outbox_events (
    id           bigint generated always as identity primary key,
    event_id     uuid         not null unique,
    event_type   varchar(60)  not null,
    aggregate_id uuid         not null,
    payload      text         not null,
    created_at   timestamptz  not null default now(),
    published_at timestamptz,
    attempts     int          not null default 0,
    last_error   varchar(500)
);

create index outbox_pending on outbox_events (id) where published_at is null;

-- Cuentas de sistema -------------------------------------------------------------

insert into accounts (id, code, kind, allow_negative)
values ('00000000-0000-0000-0000-000000000001', 'SPEI_CLEARING', 'SYSTEM', true);
