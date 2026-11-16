# Contributing

1. Fork the repository and create a feature branch: `git checkout -b feature/short-description`.
2. Run the backend tests before committing: `cd backend && mvn test`.
3. Keep controllers thin, put business rules in services, and add or update tests for every behaviour change.
4. Never commit credentials, `.env` files or real employee data.
5. Open a pull request describing what changed and how you tested it.
