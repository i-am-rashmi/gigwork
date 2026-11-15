-- A hire awaiting confirmation holds its gig (FR-27): the gig stays OPEN, but cannot be edited and cannot be held by a
-- second hire. Set and cleared only by Hiring through Gigs' own API; the flag only ever exists on an open gig.
ALTER TABLE gigs.gig ADD COLUMN hire_pending boolean NOT NULL DEFAULT false;
ALTER TABLE gigs.gig ADD CONSTRAINT gig_hire_pending_only_when_open CHECK (NOT hire_pending OR status = 'OPEN');
