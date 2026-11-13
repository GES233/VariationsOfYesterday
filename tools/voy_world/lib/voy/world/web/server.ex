defmodule Voy.World.Web.Server do
  @moduledoc """
  Bandit HTTP server wrapper for `Voy.World.Web.Router`.
  """

  @default_port 4040
  @default_ip {127, 0, 0, 1}

  def default_port, do: @default_port

  def start_link(opts \\ []) do
    port = Keyword.get(opts, :port, @default_port)
    ip = Keyword.get(opts, :ip, @default_ip)

    Bandit.start_link(
      plug: Voy.World.Web.Router,
      port: port,
      ip: ip
    )
  end

  def child_spec(opts) do
    %{
      id: __MODULE__,
      start: {__MODULE__, :start_link, [opts]},
      type: :supervisor
    }
  end
end
