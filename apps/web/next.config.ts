import type { NextConfig } from "next";

const backendUrl = process.env.BACKEND_URL || "http://127.0.0.1:8082";

const nextConfig: NextConfig = {
  transpilePackages: ["@ledger/contracts", "@ledger/ui"],
  images: {
    unoptimized: true,
  },
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
      {
        source: "/actuator/:path*",
        destination: `${backendUrl}/actuator/:path*`,
      },
      {
        source: "/v3/api-docs/:path*",
        destination: `${backendUrl}/v3/api-docs/:path*`,
      },
      {
        source: "/swagger-ui/:path*",
        destination: `${backendUrl}/swagger-ui/:path*`,
      },
    ];
  },
};

export default nextConfig;
