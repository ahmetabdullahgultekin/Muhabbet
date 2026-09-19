# Deploy

The backend runs as one container, `muhabbet-backend`, from the repo-root
`docker-compose.prod.yml` (not `infra/docker-compose.prod.yml`, which is the legacy stack and
deploys nothing). Postgres, Redis and object storage are shared services on the host's
`backend` network; Traefik on the `proxy` network routes the API.

## Secrets and configuration

- Secrets live in `.env.prod` next to the compose file: mode 0600, never committed.
  `.env.example` lists every key with placeholders.
- Compose reads it through `--env-file .env.prod`. There is no `.env` in the deploy
  directory, so without the flag every secret resolves to empty. The object storage values
  are written as `${VAR:?...}`, so a missing one stops compose before any container changes.

## Deploy command

```bash
cd /opt/projects/Muhabbet
docker compose --env-file .env.prod -f docker-compose.prod.yml build muhabbet-backend
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d muhabbet-backend
```

`infra/scripts/deploy.sh` is the same thing with a rollback tag and a health wait. Tag the
running image before a build so it can be restored:
`docker tag <image-id> muhabbet-muhabbet-backend:rollback-$(date +%F)`.

## Object storage

Media lives in an S3-compatible store; production runs [Garage](https://garagehq.deuxfleurs.fr/).
The backend talks plain S3 (bucket check, put, get, delete, presigned GET) and needs:

| Variable | Meaning |
|---|---|
| `MINIO_ENDPOINT` | S3 API as the backend reaches it inside Docker, e.g. `http://garage:3900` |
| `MINIO_PUBLIC_ENDPOINT` | Public media host that presigned URLs are rewritten to |
| `MINIO_REGION` | Signing region; Garage uses `garage` and rejects any other |
| `MINIO_BUCKET` | `muhabbet-media` |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | Per-app key granted read/write on that bucket only |
| `MINIO_PATH_STYLE` | Optional, default `true` (`http://host/bucket/key`) |

Infra owns the store: it creates the bucket, issues the key and serves anonymous reads of
`thumbnails/` on the public host. The backend never creates a bucket. At startup it checks
that the bucket exists and logs an error if not, and `/actuator/health` reports the media
component as `MEDIA_UNAVAILABLE` (without failing the deploy) for as long as it is missing
or unreachable.

**Presigned URLs and the proxy.** SigV4 signs the Host header. The backend signs against
`MINIO_ENDPOINT` and then swaps scheme and host for `MINIO_PUBLIC_ENDPOINT`, so the proxy
route for the public host must send the internal host upstream (Traefik: a service with
`passHostHeader: false` whose server URL equals `MINIO_ENDPOINT`). Forwarding the public
host makes every presigned URL fail with `SignatureDoesNotMatch`.

### Switching stores (MinIO to Garage)

1. Infra: bucket, app key and a copy of the objects exist on the new store.
2. Back up `.env.prod` outside the repo, then set the six storage values above to the new
   store's. Remove `MINIO_ROOT_PASSWORD`, which nothing reads any more.
3. Point the public media route at the new store (with the internal host header, see above).
4. Copy objects once more, deploy, remove the exited `muhabbet-minio-init` container, and
   check: `/actuator/health` answers 200, the backend log has no object storage error, an
   image message sends and opens, and a thumbnail loads from the public host.

### Rolling back

Restore the backed-up `.env.prod`, check out the previous commit's `docker-compose.prod.yml`,
point the media route back at the old store, and redeploy (or retag the rollback image).
Objects uploaded while the new store was live exist only there: copy them back to the old
store before or right after rolling back, or those messages lose their media.
