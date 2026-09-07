-- ==============================================================================
-- MediSense Module 15: Centralized Health Data Synchronization Schema
-- Supabase PostgreSQL Table Definitions, Indexes, and Row Level Security (RLS)
-- ==============================================================================

-- 1. Health Profiles Table
CREATE TABLE IF NOT EXISTS public.health_profiles (
    id TEXT PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    full_name TEXT,
    date_of_birth DATE,
    gender TEXT,
    blood_group TEXT,
    height REAL,
    weight REAL,
    allergies TEXT,
    existing_diseases TEXT,
    current_medications TEXT,
    family_history TEXT,
    emergency_contact_name TEXT,
    emergency_contact_number TEXT,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()),
    CONSTRAINT unique_user_profile UNIQUE (user_id)
);

ALTER TABLE public.health_profiles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage their own health profile"
ON public.health_profiles
FOR ALL
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_health_profiles_user_updated ON public.health_profiles(user_id, updated_at);


-- 2. Medications Table
CREATE TABLE IF NOT EXISTS public.medications (
    id BIGINT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    medicine_name TEXT NOT NULL,
    dosage TEXT DEFAULT '',
    dosage_unit TEXT DEFAULT 'mg',
    frequency TEXT DEFAULT 'ONCE_DAILY',
    scheduled_times JSONB DEFAULT '[]'::jsonb,
    start_date BIGINT NOT NULL,
    end_date BIGINT,
    instructions TEXT DEFAULT '',
    active BOOLEAN DEFAULT true,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    is_deleted BOOLEAN DEFAULT false,
    PRIMARY KEY (id, user_id)
);

ALTER TABLE public.medications ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage their own medications"
ON public.medications
FOR ALL
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_medications_user_updated ON public.medications(user_id, updated_at);


-- 3. Medication History & Adherence Table
CREATE TABLE IF NOT EXISTS public.medication_history (
    id BIGINT NOT NULL,
    medication_id BIGINT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    medicine_name TEXT NOT NULL,
    dosage TEXT DEFAULT '',
    scheduled_date BIGINT NOT NULL,
    scheduled_time TEXT NOT NULL,
    action_time BIGINT,
    status TEXT DEFAULT 'TAKEN',
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (id, user_id)
);

ALTER TABLE public.medication_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage their own medication history"
ON public.medication_history
FOR ALL
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_medication_history_user_updated ON public.medication_history(user_id, updated_at);


-- 4. Appointments Table
CREATE TABLE IF NOT EXISTS public.appointments (
    id BIGINT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    doctor_name TEXT NOT NULL,
    clinic_name TEXT NOT NULL,
    appointment_type TEXT DEFAULT 'GENERAL_CHECKUP',
    appointment_date TEXT NOT NULL,
    appointment_time TEXT NOT NULL,
    appointment_timestamp BIGINT NOT NULL,
    reminder_minutes_before INT DEFAULT 30,
    notes TEXT,
    status TEXT DEFAULT 'SCHEDULED',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    is_deleted BOOLEAN DEFAULT false,
    PRIMARY KEY (id, user_id)
);

ALTER TABLE public.appointments ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage their own appointments"
ON public.appointments
FOR ALL
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_appointments_user_updated ON public.appointments(user_id, updated_at);


-- 5. Prediction History Table
CREATE TABLE IF NOT EXISTS public.prediction_history (
    id BIGINT NOT NULL,
    user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    predicted_disease TEXT NOT NULL,
    confidence REAL NOT NULL,
    symptoms JSONB DEFAULT '[]'::jsonb,
    explanation_summary TEXT,
    prediction_timestamp BIGINT NOT NULL,
    model_version TEXT DEFAULT '1.0',
    updated_at BIGINT NOT NULL,
    PRIMARY KEY (id, user_id)
);

ALTER TABLE public.prediction_history ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage their own prediction history"
ON public.prediction_history
FOR ALL
TO authenticated
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

CREATE INDEX IF NOT EXISTS idx_prediction_history_user_updated ON public.prediction_history(user_id, updated_at);
