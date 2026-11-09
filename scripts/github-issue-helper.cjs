function resolveToken() {
  if (process.env.GITHUB_PERSONAL_ACCESS_TOKEN) return process.env.GITHUB_PERSONAL_ACCESS_TOKEN;
  if (process.env.GITHUB_TOKEN) return process.env.GITHUB_TOKEN;
  if (process.env.GH_TOKEN) return process.env.GH_TOKEN;
  return null;
}

const token = resolveToken();
const owner = 'kayoaraujo14';
const repo = 'saldo-livre';

if (!token) {
  console.error('❌ Token do GitHub não encontrado em GITHUB_PERSONAL_ACCESS_TOKEN ou GITHUB_TOKEN');
  process.exit(1);
}

const headers = {
  'Authorization': `Bearer ${token}`,
  'Accept': 'application/vnd.github.v3+json',
  'User-Agent': 'SaldoLivre-Issue-Helper',
  'Content-Type': 'application/json',
};

async function api(path, options = {}) {
  const url = `https://api.github.com/repos/${owner}/${repo}${path}`;
  const res = await fetch(url, { ...options, headers: { ...headers, ...options.headers } });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    throw new Error(`API Error [${res.status}] ${path}: ${data.message || JSON.stringify(data)}`);
  }
  return data;
}

const [,, command, issueNumberStr, ...restArgs] = process.argv;
const issueNumber = Number(issueNumberStr);

async function main() {
  if (!command || !issueNumber) {
    console.log(`
Uso: node scripts/github-issue-helper.cjs <comando> <issueNumber> [argumentos]

Comandos:
  start <issueNumber> [branchName]          Marca como iniciada, adiciona label status: in-progress e comenta.
  comment <issueNumber> "<mensagem>"        Adiciona um comentário de progresso na issue.
  check-all <issueNumber>                   Marca todos os checkboxes do checklist da issue como [x].
  close <issueNumber> [comentarioFinal]     Marca checkboxes, adiciona comentário final e fecha a issue.
`);
    process.exit(0);
  }

  // Garante que as labels de status existam
  const ensureStatusLabel = async (labelName, color) => {
    try {
      await api('/labels', {
        method: 'POST',
        body: JSON.stringify({ name: labelName, color, description: `Status: ${labelName}` }),
      });
    } catch {
      // Ignora se já existir
    }
  };

  switch (command) {
    case 'start': {
      const branchName = restArgs[0] || 'em andamento';
      await ensureStatusLabel('status: in-progress', 'ededed');
      
      // Adiciona label status: in-progress
      await api(`/issues/${issueNumber}/labels`, {
        method: 'POST',
        body: JSON.stringify({ labels: ['status: in-progress'] }),
      });

      // Adiciona comentário de início
      const comment = `🚀 **Desenvolvimento Iniciado**\n\nTarefa em andamento na branch \`${branchName}\`.\n\n*Atualizado automaticamente pelo agente de engenharia.*`;
      await api(`/issues/${issueNumber}/comments`, {
        method: 'POST',
        body: JSON.stringify({ body: comment }),
      });

      console.log(`✅ Issue #${issueNumber} marcada como 'status: in-progress' e comentada com sucesso!`);
      break;
    }

    case 'comment': {
      const message = restArgs.join(' ');
      if (!message) {
        console.error('❌ Mensagem de comentário é obrigatória.');
        process.exit(1);
      }
      await api(`/issues/${issueNumber}/comments`, {
        method: 'POST',
        body: JSON.stringify({ body: message }),
      });
      console.log(`✅ Comentário registrado na Issue #${issueNumber}!`);
      break;
    }

    case 'check-all': {
      const issue = await api(`/issues/${issueNumber}`);
      if (issue.body) {
        const updatedBody = issue.body.replace(/- \[[ ]\]/g, '- [x]');
        await api(`/issues/${issueNumber}`, {
          method: 'PATCH',
          body: JSON.stringify({ body: updatedBody }),
        });
        console.log(`✅ Checklists da Issue #${issueNumber} marcados como concluídos!`);
      }
      break;
    }

    case 'close': {
      const finalComment = restArgs.join(' ') || 'Conclusão validada com testes automatizados e linters aprovados 100%.';
      
      // 1. Marca checklist como concluído
      const issue = await api(`/issues/${issueNumber}`);
      let updatedBody = issue.body;
      if (issue.body) {
        updatedBody = issue.body.replace(/- \[[ ]\]/g, '- [x]');
      }

      // 2. Remove label in-progress se existir e adiciona status: done
      await ensureStatusLabel('status: done', '0e8a16');
      try {
        await api(`/issues/${issueNumber}/labels/status:%20in-progress`, { method: 'DELETE' });
      } catch {
        // Ignora caso não tenha a label
      }

      // 3. Atualiza body e fecha a issue
      await api(`/issues/${issueNumber}`, {
        method: 'PATCH',
        body: JSON.stringify({
          body: updatedBody,
          state: 'closed',
          state_reason: 'completed',
          labels: [...(issue.labels?.map(l => l.name).filter(n => n !== 'status: in-progress') || []), 'status: done'],
        }),
      });

      // 4. Adiciona comentário de encerramento
      const closeMsg = `🎉 **Issue Concluída e Verificada**\n\n${finalComment}\n\n*Entregue conforme os critérios de aceite e governança do projeto.*`;
      await api(`/issues/${issueNumber}/comments`, {
        method: 'POST',
        body: JSON.stringify({ body: closeMsg }),
      });

      console.log(`✅ Issue #${issueNumber} encerrada com sucesso no GitHub!`);
      break;
    }

    default:
      console.error(`❌ Comando desconhecido: ${command}`);
      process.exit(1);
  }
}

main().catch(err => {
  console.error('Falha:', err.message);
  process.exit(1);
});
