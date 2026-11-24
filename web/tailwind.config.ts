import type { Config } from "tailwindcss";

const config: Config = {
  content: [
    "./src/pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/components/**/*.{js,ts,jsx,tsx,mdx}",
    "./src/app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "#F8FAFC",
        surface: "#FFFFFF",
        primary: {
          50: "#F0F5FF",
          100: "#EAF1FF",
          500: "#2563EB",
          600: "#1D4ED8",
        },
        accent: {
          blue: "#2563EB",
          purple: "#8B5CF6",
        },
        income: {
          DEFAULT: "#16A34A",
          light: "#DCFCE7",
          dark: "#15803D",
        },
        expense: {
          DEFAULT: "#E85D75",
          light: "#FFE4E8",
          dark: "#D13C57",
        },
        muted: "#F1F5F9",
      },
      borderRadius: {
        "glass": "20px",
        "glass-sm": "14px",
        "glass-lg": "28px",
      },
      boxShadow: {
        "glass": "0 8px 32px 0 rgba(31, 38, 135, 0.06)",
        "glass-card": "0 10px 30px -5px rgba(0, 0, 0, 0.04), 0 0 1px 1px rgba(255, 255, 255, 0.8)",
        "glass-hover": "0 20px 40px -10px rgba(0, 0, 0, 0.08), 0 0 2px 1px rgba(255, 255, 255, 0.9)",
      },
      backdropBlur: {
        "glass": "20px",
        "glass-lg": "24px",
      },
    },
  },
  plugins: [],
};

export default config;
