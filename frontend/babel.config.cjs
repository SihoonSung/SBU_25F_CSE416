module.exports = {
  presets: [
    ['@babel/preset-env', { targets: { node: 'current' } }],
    ['@babel/preset-react', { runtime: 'automatic' }],
    '@babel/preset-typescript',
  ],
  plugins: [
    [
      'babel-plugin-transform-import-meta',
      {
        module: 'CommonJS',
        replace: {
          'import.meta.env.VITE_BACKEND_URL': 'process.env.VITE_BACKEND_URL',
        },
      },
    ],
  ],
};

