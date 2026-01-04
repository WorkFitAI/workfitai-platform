// Seed admin and test HR_MANAGER accounts for auth-service
// V002__seed_admin_account.js
// UUIDs match user-service for cross-service consistency

// UUIDs for consistency across services:
// Admin:      00000000-0000-0000-0000-000000000001
// HR Manager: 00000000-0000-0000-0000-000000000002
// HR Staff:   00000000-0000-0000-0000-000000000003

// Insert admin user with hashed password (password: admin123)
db.users.insertOne({
    "_id": "00000000-0000-0000-0000-000000000001",
    "username": "admin",
    "email": "admin@workfitai.com",
    "password": "$2a$10$Brs6KoUQKyEAk4A/bX9IM.GU3WyoAUqCvRENCcjyYBrL35qcnTV2C",
    "roles": ["ADMIN"],
    "status": "ACTIVE",
    "company": null,
    "createdAt": new Date(),
    "updatedAt": new Date()
});

// Insert test HR_MANAGER for development/testing (password: hrmanager123)
db.users.insertOne({
    "_id": "00000000-0000-0000-0000-000000000002",
    "username": "hrmanager_techcorp",
    "email": "hrmanager@techcorp.com",
    "password": "$2a$10$Pqtbiw/2PLYxTdz9011OSezC5TbpVjYMYk3gI2pPBZxXBLRe6N4VC",
    "roles": ["HR_MANAGER"],
    "status": "ACTIVE",
    "company": "TechCorp Solutions",
    "createdAt": new Date(),
    "updatedAt": new Date()
});

// Insert test HR for development/testing (password: hr123)
db.users.insertOne({
    "_id": "00000000-0000-0000-0000-000000000003",
    "username": "hr_techcorp",
    "email": "hr@techcorp.com",
    "password": "$2a$10$guDe/4myTKCoPKNJc6HgCeyHjbm.z8AeeompYSB1Of/.EpLUuxFHi",
    "roles": ["HR"],
    "status": "ACTIVE",
    "company": "TechCorp Solutions",
    "createdAt": new Date(),
    "updatedAt": new Date()
});

// Note: This is a MongoDB migration script for auth-service
// Passwords (BCrypt hashed):
// - admin@workfitai.com: admin123
// - hrmanager@techcorp.com: hrmanager123  
// - hr@techcorp.com: hr123
// Please change these passwords after first login in production