// MongoDB Collections Initialization for auth-service User Profile Features
// Run this in MongoDB shell or through initialization script

// Use auth-db database
use('auth-db');

// ========================================
// 1. User Sessions Collection
// ========================================
db.createCollection('user_sessions', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['userId', 'sessionId', 'refreshTokenHash', 'deviceId', 'createdAt', 'expiresAt'],
            properties: {
                userId: {
                    bsonType: 'string',
                    description: 'User ID - required'
                },
                sessionId: {
                    bsonType: 'string',
                    description: 'Unique session identifier - required'
                },
                refreshTokenHash: {
                    bsonType: 'string',
                    description: 'Hashed refresh token - required'
                },
                deviceId: {
                    bsonType: 'string',
                    description: 'Unique device identifier - required'
                },
                deviceName: {
                    bsonType: 'string',
                    description: 'Device name (browser, OS, etc.)'
                },
                ipAddress: {
                    bsonType: 'string',
                    description: 'IP address of the device'
                },
                userAgent: {
                    bsonType: 'string',
                    description: 'Full user agent string'
                },
                location: {
                    bsonType: 'object',
                    properties: {
                        country: { bsonType: 'string' },
                        city: { bsonType: 'string' },
                        region: { bsonType: 'string' },
                        latitude: { bsonType: 'double' },
                        longitude: { bsonType: 'double' }
                    }
                },
                createdAt: {
                    bsonType: 'date',
                    description: 'Session creation timestamp - required'
                },
                lastActivityAt: {
                    bsonType: 'date',
                    description: 'Last activity timestamp'
                },
                expiresAt: {
                    bsonType: 'date',
                    description: 'Session expiration timestamp - required'
                }
            }
        }
    }
});

// Indexes for user_sessions
db.user_sessions.createIndex({ userId: 1, sessionId: 1 }, { unique: true });
db.user_sessions.createIndex({ sessionId: 1 }, { unique: true });
db.user_sessions.createIndex({ userId: 1 });
db.user_sessions.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 }); // TTL index
db.user_sessions.createIndex({ deviceId: 1 });
db.user_sessions.createIndex({ createdAt: -1 });

print('✓ Created user_sessions collection with indexes');

// ========================================
// 2. Password Reset Tokens Collection
// ========================================
db.createCollection('password_reset_tokens', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['email', 'token', 'otp', 'createdAt', 'expiresAt'],
            properties: {
                email: {
                    bsonType: 'string',
                    pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$',
                    description: 'User email address - required'
                },
                token: {
                    bsonType: 'string',
                    description: 'Unique reset token - required'
                },
                otp: {
                    bsonType: 'string',
                    pattern: '^[0-9]{6}$',
                    description: '6-digit OTP code - required'
                },
                createdAt: {
                    bsonType: 'date',
                    description: 'Token creation timestamp - required'
                },
                expiresAt: {
                    bsonType: 'date',
                    description: 'Token expiration timestamp - required'
                },
                used: {
                    bsonType: 'bool',
                    description: 'Whether token has been used'
                },
                usedAt: {
                    bsonType: 'date',
                    description: 'Timestamp when token was used'
                },
                attempts: {
                    bsonType: 'int',
                    minimum: 0,
                    description: 'Number of failed verification attempts'
                }
            }
        }
    }
});

// Indexes for password_reset_tokens
db.password_reset_tokens.createIndex({ token: 1 }, { unique: true });
db.password_reset_tokens.createIndex({ email: 1 });
db.password_reset_tokens.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 }); // TTL index
db.password_reset_tokens.createIndex({ used: 1, expiresAt: 1 });
db.password_reset_tokens.createIndex({ createdAt: -1 });

print('✓ Created password_reset_tokens collection with indexes');

// ========================================
// 3. Two-Factor Authentication Collection
// ========================================
db.createCollection('two_factor_auth', {
    validator: {
        $jsonSchema: {
            bsonType: 'object',
            required: ['userId', 'method', 'enabled'],
            properties: {
                userId: {
                    bsonType: 'string',
                    description: 'User ID - required'
                },
                method: {
                    enum: ['TOTP', 'EMAIL'],
                    description: '2FA method: TOTP or EMAIL - required'
                },
                secret: {
                    bsonType: 'string',
                    description: 'TOTP secret key (required for TOTP method)'
                },
                backupCodes: {
                    bsonType: 'array',
                    items: {
                        bsonType: 'object',
                        required: ['code', 'used'],
                        properties: {
                            code: { bsonType: 'string' },
                            used: { bsonType: 'bool' },
                            usedAt: { bsonType: 'date' }
                        }
                    },
                    description: 'Array of backup codes with usage status'
                },
                enabled: {
                    bsonType: 'bool',
                    description: 'Whether 2FA is enabled - required'
                },
                enabledAt: {
                    bsonType: 'date',
                    description: 'Timestamp when 2FA was enabled'
                },
                disabledAt: {
                    bsonType: 'date',
                    description: 'Timestamp when 2FA was disabled'
                },
                lastVerifiedAt: {
                    bsonType: 'date',
                    description: 'Last successful 2FA verification'
                },
                verificationAttempts: {
                    bsonType: 'int',
                    minimum: 0,
                    description: 'Failed verification attempts counter'
                }
            }
        }
    }
});

// Indexes for two_factor_auth
db.two_factor_auth.createIndex({ userId: 1 }, { unique: true });
db.two_factor_auth.createIndex({ enabled: 1 });
db.two_factor_auth.createIndex({ method: 1 });

print('✓ Created two_factor_auth collection with indexes');

// ========================================
// Summary
// ========================================
print('\n========================================');
print('MongoDB Collections Initialization Complete!');
print('========================================');
print('Collections created:');
print('  1. user_sessions (with TTL index on expiresAt)');
print('  2. password_reset_tokens (with TTL index on expiresAt)');
print('  3. two_factor_auth');
print('\nAll indexes created successfully.');
print('========================================\n');
