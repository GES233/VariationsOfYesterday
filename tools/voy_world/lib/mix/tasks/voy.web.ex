defmodule Mix.Tasks.Voy.Web do
  @shortdoc "Start the local Voy.World interactive WebUI (Plug + Bandit)"

  @moduledoc """
  Starts the local `Voy.World` inspection and reasoning workbench:

      mix voy.web [--port 4040] [--host 127.0.0.1] [--open]
  """

  use Mix.Task

  @impl true
  def run(argv) do
    {opts, _args, invalid} =
      OptionParser.parse(argv, strict: [port: :integer, host: :string, open: :boolean])

    if invalid != [] do
      [{flag, _} | _] = invalid
      Mix.raise("无法识别的参数 #{flag}；用法：mix voy.web [--port 4040] [--host 127.0.0.1] [--open]")
    end

    Mix.Task.run("app.start")

    port = Keyword.get(opts, :port, Voy.World.Web.Server.default_port())
    host = Keyword.get(opts, :host, "127.0.0.1")
    ip = parse_ip!(host)

    case Voy.World.Web.Server.start_link(port: port, ip: ip) do
      {:ok, _pid} ->
        url = "http://#{host}:#{port}/"
        Mix.shell().info("Voy.World WebUI listening at #{url} (Ctrl+C to stop)")
        if Keyword.get(opts, :open, false), do: open_browser(url)
        Process.sleep(:infinity)

      {:error, reason} ->
        Mix.raise("无法启动 WebUI（端口 #{port}）：#{inspect(reason)}")
    end
  end

  defp parse_ip!(host) do
    case :inet.parse_address(String.to_charlist(host)) do
      {:ok, ip} -> ip
      {:error, _} -> Mix.raise("非法 host 地址：#{host}")
    end
  end

  defp open_browser(url) do
    case :os.type() do
      {:win32, _} -> System.cmd("cmd", ["/c", "start", "", url])
      {:unix, :darwin} -> System.cmd("open", [url])
      {:unix, _} -> System.cmd("xdg-open", [url])
    end
  rescue
    _ -> :ok
  end
end
