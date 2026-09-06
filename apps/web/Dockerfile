FROM node:24-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
# Local-only: makes the synthetic-content source explainer page reachable so the seed data
# written by LocalDataInitializer (see apps/api) has somewhere to link to. Never set on the
# candidate/production image (Dockerfile.candidate), so that build compiles this route out.
ENV VITE_LOCAL_FIXTURE_SOURCES_ENABLED=true
RUN npm run build

FROM nginx:1.29-alpine
COPY --from=build /workspace/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
