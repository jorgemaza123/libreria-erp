#!/bin/bash
# 1. Definir variables
FECHA=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups"
DB_USER="postgres"
DB_NAME="libreria_db"

# 2. Crear respaldo
pg_dump -U $DB_USER $DB_NAME > "$BACKUP_DIR/backup_$FECHA.sql"

# 3. Eliminar copias mayores a 30 días
find $BACKUP_DIR -type f -name "*.sql" -mtime +30 -delete