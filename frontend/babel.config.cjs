module.exports = {
  presets: [
    ['@babel/preset-env', { targets: { node: 'current' } }],
    ['@babel/preset-react', { runtime: 'automatic' }],
    '@babel/preset-typescript',
  ],
  plugins: [({ types }) => ({
    visitor: {
      MetaProperty(path) {
        const parent = path.parentPath;
        if (path.node.meta.name === 'import' && path.node.property.name === 'meta' &&
            parent.isMemberExpression({ object: path.node }) &&
            types.isIdentifier(parent.node.property, { name: 'env' }) && !parent.node.computed) {
          parent.replaceWith(types.memberExpression(types.identifier('process'), types.identifier('env')));
        }
      },
    },
  })],
};

