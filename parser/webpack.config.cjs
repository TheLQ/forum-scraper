const path = require('path');

module.exports = {
  // entry: './src/parser.ts',
  entry: './src/web.ts',
  devtool: 'inline-source-map',
  target: 'node',
  mode: "production",
  module: {
    rules: [
      {
        test: /\.tsx?$/,
        use: 'ts-loader',
        exclude: /node_modules/,
      },
    ],
  },
  resolve: {
    extensions: ['.tsx', '.ts', '.js'],
  },
  output: {
    filename: 'bundle.js',
    path: path.resolve(__dirname, 'dist'),
  },
};