-- Create the health_profiles table in Supabase PostgreSQL
CREATE TABLE IF NOT EXISTS public.health_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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

-- Enable Row Level Security (RLS)
ALTER TABLE public.health_profiles ENABLE ROW LEVEL SECURITY;

-- RLS Policy: Users can insert their own health profile
CREATE POLICY "Users can insert their own health profile" 
ON public.health_profiles 
FOR INSERT 
TO authenticated 
WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can select their own health profile
CREATE POLICY "Users can select their own health profile" 
ON public.health_profiles 
FOR SELECT 
TO authenticated 
USING (auth.uid() = user_id);

-- RLS Policy: Users can update their own health profile
CREATE POLICY "Users can update their own health profile" 
ON public.health_profiles 
FOR UPDATE 
TO authenticated 
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

-- RLS Policy: Users can delete their own health profile
CREATE POLICY "Users can delete their own health profile" 
ON public.health_profiles 
FOR DELETE 
TO authenticated 
USING (auth.uid() = user_id);
