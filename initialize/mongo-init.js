// MongoDB Initialization Script for cv-db
// This script runs when the MongoDB container is first created

db = db.getSiblingDB("cv-db");

// Create application user with read/write permissions
db.createUser({
  user: "user",
  pwd: "123456",
  roles: [
    {
      role: "readWrite",
      db: "cv-db",
    },
  ],
});

print("✅ CV database initialized successfully");
print("   - Database: cv-db");
print("   - User: user");
print("   - Roles: readWrite on cv-db");
