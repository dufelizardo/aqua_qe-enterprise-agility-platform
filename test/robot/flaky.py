"""Lista os testes instáveis: falharam na primeira execução e passaram na reexecução.

Uso: python3 flaky.py first.xml rerun.xml  (um nome completo de teste por linha na saída)
"""
import sys
import xml.etree.ElementTree as ET


def statuses(path):
    """Mapeia o nome completo de cada teste (suíte.teste) para o status final."""
    result = {}

    def walk(suite, prefix):
        name = f"{prefix}.{suite.get('name')}" if prefix else suite.get('name')
        for child in suite.findall('suite'):
            walk(child, name)
        for test in suite.findall('test'):
            status = test.find('status')
            result[f"{name}.{test.get('name')}"] = status.get('status') if status is not None else 'FAIL'

    walk(ET.parse(path).getroot().find('suite'), '')
    return result


def main():
    first, rerun = statuses(sys.argv[1]), statuses(sys.argv[2])
    for name, status in first.items():
        if status == 'FAIL' and rerun.get(name) == 'PASS':
            print(name)


if __name__ == '__main__':
    main()
