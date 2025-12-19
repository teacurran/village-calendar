-- //
-- Create user_mazes table for the maze generator
-- Requires: 001_initial_schema.sql (for calendar_users table and uuid-ossp extension)
-- //

-- Create user_mazes table for storing generated mazes
CREATE TABLE user_mazes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID,
    session_id VARCHAR(255),
    is_public BOOLEAN NOT NULL DEFAULT true,
    name VARCHAR(255) NOT NULL,
    maze_type VARCHAR(20) NOT NULL DEFAULT 'ORTHOGONAL',
    size INTEGER NOT NULL DEFAULT 10,
    difficulty INTEGER NOT NULL DEFAULT 3,
    seed BIGINT,
    configuration JSONB,
    generated_svg TEXT,
    generated_pdf_url VARCHAR(500),
    solution_path JSONB,
    created TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_user_mazes_user FOREIGN KEY (user_id) REFERENCES calendar_users(id) ON DELETE CASCADE,
    CONSTRAINT chk_user_mazes_size CHECK (size >= 1 AND size <= 20),
    CONSTRAINT chk_user_mazes_difficulty CHECK (difficulty >= 1 AND difficulty <= 5),
    CONSTRAINT chk_user_mazes_maze_type CHECK (maze_type IN ('ORTHOGONAL', 'DELTA', 'SIGMA', 'THETA'))
);

CREATE INDEX idx_user_mazes_user ON user_mazes(user_id, created DESC);
CREATE INDEX idx_user_mazes_session ON user_mazes(session_id, updated DESC);
CREATE INDEX idx_user_mazes_public ON user_mazes(is_public, updated DESC);

-- Add comments for documentation
COMMENT ON TABLE user_mazes IS 'User-created mazes with customizations, supports both authenticated users and anonymous sessions';
COMMENT ON COLUMN user_mazes.user_id IS 'Reference to authenticated user (nullable for anonymous sessions)';
COMMENT ON COLUMN user_mazes.session_id IS 'Session identifier for anonymous users';
COMMENT ON COLUMN user_mazes.maze_type IS 'Maze tessellation type: ORTHOGONAL (square), DELTA (triangular), SIGMA (hexagonal), THETA (circular)';
COMMENT ON COLUMN user_mazes.size IS 'Size level 1-20, controls cell count (1=~15 cells, 20=~100 cells)';
COMMENT ON COLUMN user_mazes.difficulty IS 'Difficulty level 1-5, controls shortcuts (1=easy, 5=hard)';
COMMENT ON COLUMN user_mazes.seed IS 'Random seed for reproducible maze generation';
COMMENT ON COLUMN user_mazes.configuration IS 'JSONB field for additional options (showSolution, innerWallColor, outerWallColor, pathColor)';
COMMENT ON COLUMN user_mazes.solution_path IS 'JSON array of cell coordinates representing the solution path';

-- //@UNDO

-- Drop table
DROP TABLE IF EXISTS user_mazes;
