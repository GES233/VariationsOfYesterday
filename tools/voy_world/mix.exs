defmodule Voy.World.MixProject do
  use Mix.Project

  def project do
    [
      app: :voy_world,
      version: "0.1.0",
      elixir: "~> 1.19",
      start_permanent: Mix.env() == :prod,
      test_ignore_filters: [~r/^test\/support\//],
      deps: []
    ]
  end

  def application do
    [extra_applications: [:logger]]
  end
end
